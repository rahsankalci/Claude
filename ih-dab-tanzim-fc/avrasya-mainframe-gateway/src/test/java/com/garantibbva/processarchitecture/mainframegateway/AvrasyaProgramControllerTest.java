package com.garantibbva.processarchitecture.mainframegateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AvrasyaProgramControllerTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static HttpServer bpm;
    private static final BlockingQueue<String[]> GERI_DONUSLER = new ArrayBlockingQueue<>(10);

    @Autowired
    private TestRestTemplate rest;

    /** BPM geri donus ucu stub'i. */
    @BeforeAll
    static void bpmBaslat() throws Exception {
        bpm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        bpm.createContext("/api/avrasya/geri-donus/", ex -> {
            GERI_DONUSLER.add(new String[]{ex.getRequestURI().getPath(),
                    new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    ex.getRequestHeaders().getFirst("Authorization")});
            byte[] b = "{\"tamamlandi\":true}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        bpm.start();
    }

    @AfterAll
    static void bpmDurdur() {
        bpm.stop(0);
    }

    private String cb() {
        return "http://127.0.0.1:" + bpm.getAddress().getPort() + "/api/avrasya/geri-donus/5/14";
    }

    @Test
    void senkronReferansProgrami() throws Exception {
        ResponseEntity<String> r = rest.postForEntity("/avrasya/54866",
                Map.of("programId", "54866", "processInstanceId", 5, "workItemId", 13,
                        "data", Map.of("sGK_sWIP", Map.of())), String.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("0", M.readTree(r.getBody()).at("/data/sGK_sWIP/status_code").asText());
    }

    @Test
    void asenkronWebClient_MainframeSonucuBpmeGeriDoner() throws Exception {
        ResponseEntity<String> r = rest.postForEntity("/avrasya/269812",
                Map.of("programId", "269812", "processInstanceId", 5, "workItemId", 14, "callbackUrl", cb(),
                        "data", Map.of("sGK_sWID", Map.of("musteri_no", "98765"))), String.class);
        assertEquals(HttpStatus.ACCEPTED, r.getStatusCode());

        String[] geri = GERI_DONUSLER.poll(10, TimeUnit.SECONDS);
        assertEquals("/api/avrasya/geri-donus/5/14", geri[0]);
        JsonNode body = M.readTree(geri[1]);
        assertEquals("OK", body.at("/data/sGK_sOUT/mf_sonuc").asText());
        assertEquals("98765", body.at("/data/sGK_sWID/musteri_no").asText(), "konteyner korunmali");
        assertTrue(geri[2].startsWith("Basic "));
    }

    @Test
    void asenkronMainframeHatasiServiceErrorOlarakDoner() throws Exception {
        rest.postForEntity("/avrasya/269812", Map.of("callbackUrl", cb(), "data", Map.of("simule_hata", "1")), String.class);
        String[] geri = GERI_DONUSLER.poll(10, TimeUnit.SECONDS);
        assertTrue(M.readTree(geri[1]).get("serviceError").asText().contains("ABEND"));
    }

    @Test
    void senkronMainframeHatasi502() {
        ResponseEntity<String> r = rest.postForEntity("/avrasya/116094", Map.of("data", Map.of("simule_hata", "1")), String.class);
        assertEquals(HttpStatus.BAD_GATEWAY, r.getStatusCode());
    }

    @Test
    void asenkrondaCallbackZorunlu() {
        ResponseEntity<String> r = rest.postForEntity("/avrasya/269812", Map.of("data", Map.of()), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }
}
