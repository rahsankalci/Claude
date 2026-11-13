package com.garantibbva.processarchitecture.mainframegateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * BPM'in (AvrasyaServiceWorkItemHandler) cagirdigi Avrasya program ucu.
 *
 * <pre>
 * POST /avrasya/{programId}
 * {"programId","activityName","processInstanceId","workItemId","callbackUrl","data":{...}}
 * </pre>
 * <ul>
 *   <li>senkron program: mainframe cagrilir; 200 {"data":...} ya da 502 {"error":...}</li>
 *   <li>asenkron program ({@code gateway.programs.<id>.mode=async}): 202 doner; mainframe cagrisi
 *       arka planda yapilir ve sonuc {@code callbackUrl}'e {"data":...} / {"serviceError":...}
 *       olarak POST edilir (mainframe -> BPM geri donus).</li>
 * </ul>
 */
@RestController
public class AvrasyaProgramController {

    private static final Logger log = LoggerFactory.getLogger(AvrasyaProgramController.class);
    private static final int GERI_DONUS_DENEME = 5;

    private final MainframeConnector mainframe;
    private final GatewayProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService havuz = Executors.newFixedThreadPool(8);

    public AvrasyaProgramController(MainframeConnector mainframe, GatewayProperties props, ObjectMapper mapper) {
        this.mainframe = mainframe;
        this.props = props;
        this.mapper = mapper;
    }

    @PostMapping("/avrasya/{programId}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> calistir(@PathVariable String programId,
                                                        @RequestBody Map<String, Object> istek) {
        Object d = istek.get("data");
        if (!(d instanceof Map)) {
            return ResponseEntity.badRequest().body(Map.of("error", "'data' (konteyner) zorunlu"));
        }
        Map<String, Object> data = (Map<String, Object>) d;
        Object pid = istek.get("processInstanceId");

        if (props.isAsync(programId)) {
            Object cb = istek.get("callbackUrl");
            if (cb == null) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Asenkron program " + programId + " icin callbackUrl zorunlu (BPM: avrasya.service.callback-url)"));
            }
            log.info("Asenkron mainframe istegi kabul edildi :: program={}, pid={}, wid={}", programId, pid, istek.get("workItemId"));
            havuz.submit(() -> asenkronCalistir(programId, data, String.valueOf(cb)));
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }

        try {
            Map<String, Object> out = mainframe.call(programId, data);
            log.info("Mainframe programi tamamlandi :: program={}, pid={}", programId, pid);
            return ResponseEntity.ok(Map.of("data", out));
        } catch (MainframeConnector.MainframeException e) {
            log.error("Mainframe programi hata :: program={}, pid={} -> {}", programId, pid, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    private void asenkronCalistir(String programId, Map<String, Object> data, String callbackUrl) {
        Map<String, Object> sonuc = new LinkedHashMap<>();
        try {
            sonuc.put("data", mainframe.call(programId, data));
        } catch (RuntimeException e) {
            log.error("Asenkron mainframe programi hata :: program={} -> {}", programId, e.getMessage());
            sonuc.put("serviceError", e.getMessage());
        }
        bpmGeriDon(callbackUrl, sonuc);
    }

    /** Mainframe sonucunu BPM'e geri dondurur; BPM gecici erisilemezse tekrar dener. */
    void bpmGeriDon(String callbackUrl, Map<String, Object> sonuc) {
        for (int i = 1; i <= GERI_DONUS_DENEME; i++) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(callbackUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(sonuc), StandardCharsets.UTF_8));
                GatewayProperties.Bpm bpm = props.getBpm();
                if (bpm.getUsername() != null) {
                    b.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                            (bpm.getUsername() + ":" + bpm.getPassword()).getBytes(StandardCharsets.UTF_8)));
                }
                HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() / 100 == 2) {
                    log.info("BPM'e geri donuldu :: {} -> HTTP {}", callbackUrl, r.statusCode());
                    return;
                }
                if (r.statusCode() == 404 || r.statusCode() == 409 || r.statusCode() == 400) {
                    log.error("BPM geri donusu reddetti (tekrar denenmeyecek) :: {} -> HTTP {} {}", callbackUrl, r.statusCode(), r.body());
                    return;
                }
                log.warn("BPM geri donusu basarisiz ({}/{}) :: HTTP {}", i, GERI_DONUS_DENEME, r.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("BPM geri donusu basarisiz ({}/{}) :: {}", i, GERI_DONUS_DENEME, e.getMessage());
            }
            try {
                Thread.sleep(1000L * i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.error("BPM'e geri donulemedi, is BPM'de bekliyor :: {}", callbackUrl);
    }

    @PreDestroy
    void kapat() {
        havuz.shutdown();
    }
}
