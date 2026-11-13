package com.garantibbva.processarchitecture.ihdabtanzimfc.handler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Avrasya'daki otomatik aktivitelerin (StartOption=A + ProgramID) karsiligi.
 *
 * <p>Girdi parametreleri:
 * <ul>
 *   <li>{@code ProgramID}   - Avrasya program numarasi (116093, 116094, ...)</li>
 *   <li>{@code ActivityName} - Avrasya aktivite adi (loglama / servis tarafinda bilgi)</li>
 *   <li>{@code Data}        - Surec konteyneri (sExport_sALL). Avrasya'daki DDC
 *       eslemesindeki gibi konteynerin TAMAMI servise gider ve donus TAMAMI geri yazilir.</li>
 * </ul>
 * Cikti parametreleri:
 * <ul>
 *   <li>{@code Data}         - Servisin dondurdugu guncel konteyner (hata halinde girdi aynen)</li>
 *   <li>{@code ServiceError} - Teknik hata mesaji; basarili cagrida {@code null}</li>
 * </ul>
 *
 * <p>Handler HICBIR ZAMAN istisna firlatmaz; teknik hatayi {@code ServiceError} olarak
 * dondurur. Avrasya'daki "EndCondition" (return_code="00" vb.) BPMN tarafinda
 * kontrol edilir; saglanmazsa is, Avrasya'daki WorkQueue 585'e karsilik gelen
 * "Hata Kuyrugu" insan gorevine duser (Tekrar Dene / Atla).
 *
 * <p>HTTP sozlesmesi: {@code POST <url>} govde:
 * <pre>{"programId":"116093","activityName":"...","processInstanceId":1,"workItemId":7,
 *  "callbackUrl":"http://bpm/api/avrasya/geri-donus/1/7","data":{...}}</pre>
 * <ul>
 *   <li><b>Senkron</b> (varsayilan): yanit 2xx; govde {@code {"data":{...}}} ya da dogrudan konteyner.</li>
 *   <li><b>Asenkron</b> (mainframe'e gidip BPM'e geri donen programlar): servis {@code 202 Accepted}
 *       doner (veya program {@code mode=async} ayarlidir). Work item tamamlanMAZ; surec
 *       guvenli bekleme noktasinda DB'ye yazilir. Mainframe isi bitirince
 *       {@code callbackUrl}'e {@code {"data":{...}}} (veya {@code {"serviceError":"..."}}) POST eder;
 *       BPM work item'i tamamlar ve akis kaldigi yerden devam eder (herhangi bir pod'da).</li>
 * </ul>
 */
public class AvrasyaServiceWorkItemHandler implements WorkItemHandler {

    public static final String NAME = "AvrasyaService";
    public static final String P_PROGRAM_ID = "ProgramID";
    public static final String P_ACTIVITY_NAME = "ActivityName";
    public static final String P_DATA = "Data";
    public static final String R_DATA = "Data";
    public static final String R_SERVICE_ERROR = "ServiceError";

    private static final Logger log = LoggerFactory.getLogger(AvrasyaServiceWorkItemHandler.class);
    private static final int MAX_ERROR_BODY = 500;
    /** call() sonucu: istek asenkron kabul edildi. */
    private static final Object ASYNC = new Object();

    private final AvrasyaServiceConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    /** kie-deployment-descriptor.xml'den (mvel resolver) kullanilan kurucu. */
    public AvrasyaServiceWorkItemHandler() {
        this(AvrasyaServiceConfig.fromEnvironment());
    }

    public AvrasyaServiceWorkItemHandler(AvrasyaServiceConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.timeout()).build());
    }

    public AvrasyaServiceWorkItemHandler(AvrasyaServiceConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        String programId = String.valueOf(workItem.getParameter(P_PROGRAM_ID));
        String activityName = (String) workItem.getParameter(P_ACTIVITY_NAME);
        Object data = workItem.getParameter(P_DATA);

        Map<String, Object> results = new HashMap<>();
        results.put(R_DATA, data);
        results.put(R_SERVICE_ERROR, null);
        try {
            Object out = call(workItem, programId, activityName, data);
            if (out == ASYNC) {
                log.info("Avrasya servisi asenkron kabul edildi, geri donus bekleniyor :: pid={}, workItem={}, program={}, aktivite={}",
                        workItem.getProcessInstanceId(), workItem.getId(), programId, activityName);
                return; // work item acik kalir; geri donus ucu tamamlar
            }
            results.put(R_DATA, out);
            log.info("Avrasya servisi tamamlandi :: pid={}, program={}, aktivite={}",
                    workItem.getProcessInstanceId(), programId, activityName);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("Avrasya servisi hata :: pid={}, program={}, aktivite={} -> {}",
                    workItem.getProcessInstanceId(), programId, activityName, msg, e);
            results.put(R_SERVICE_ERROR, msg);
        }
        manager.completeWorkItem(workItem.getId(), results);
    }

    private Object call(WorkItem workItem, String programId, String activityName, Object data) throws Exception {
        if (data == null) {
            throw new IllegalArgumentException("'" + P_DATA + "' parametresi bos");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("programId", programId);
        body.put("activityName", activityName);
        body.put("processInstanceId", workItem.getProcessInstanceId());
        body.put("workItemId", workItem.getId());
        String cb = config.callbackUrl();
        if (cb != null) {
            body.put("callbackUrl", (cb.endsWith("/") ? cb : cb + "/") + workItem.getProcessInstanceId() + "/" + workItem.getId());
        }
        body.put("data", data);

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(config.resolveUrl(programId)))
                .timeout(config.timeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Correlation-Id", workItem.getProcessInstanceId() + "-" + workItem.getId())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
        String auth = config.authHeader();
        if (auth != null) {
            req.header("Authorization", auth);
        }

        HttpResponse<String> resp = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            String b = resp.body() == null ? "" : resp.body();
            throw new IllegalStateException("HTTP " + resp.statusCode() + " - "
                    + (b.length() > MAX_ERROR_BODY ? b.substring(0, MAX_ERROR_BODY) + "..." : b));
        }
        if (resp.statusCode() == 202 || config.isAsync(programId)) {
            return ASYNC;
        }
        if (resp.body() == null || resp.body().isBlank()) {
            return data; // servis govde donmediyse konteyner degismez
        }
        JsonNode node = mapper.readTree(resp.body());
        JsonNode payload = node.has("data") && node.get("data").isObject() ? node.get("data") : node;
        return mapper.treeToValue(payload, data.getClass());
    }

    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        // Senkron cagri; iptal edilecek bekleyen istek yok.
    }
}
