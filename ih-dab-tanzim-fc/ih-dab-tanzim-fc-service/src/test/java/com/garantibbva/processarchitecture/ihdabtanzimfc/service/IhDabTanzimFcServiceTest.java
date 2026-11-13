package com.garantibbva.processarchitecture.ihdabtanzimfc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Uygulamayi gercek KIE Server (H2) ile ayaga kaldirir; Avrasya servislerini yerel bir
 * HTTP stub'i karsilar. Surec REST uzerinden baslatilip tum gorevler entegrasyon
 * ucuyla tamamlanir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IhDabTanzimFcServiceTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final List<String> CAGRILAR = Collections.synchronizedList(new ArrayList<>());
    private static final HttpServer AVRASYA = stub();

    /** Mainframe'in BPM geri donus ucuna verdigi son HTTP durum kodu. */
    private static final AtomicInteger GERI_DONUS_HTTP = new AtomicInteger();

    @Autowired
    private TestRestTemplate rest;

    @LocalServerPort
    private int port;

    @BeforeEach
    void geriDonusAdresi() {
        System.setProperty("avrasya.service.callback-url", "http://127.0.0.1:" + port + "/api/avrasya/geri-donus");
    }

    /** Avrasya servis stub'i: konteyneri basari kodlariyla geri dondurur. */
    private static HttpServer stub() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/avrasya/", ex -> {
                JsonNode req = M.readTree(ex.getRequestBody());
                String program = req.get("programId").asText();
                CAGRILAR.add(program);
                ObjectNode data = (ObjectNode) req.get("data");
                if ("269812".equals(program)) {
                    // WebClient: mainframe istegi kabul eder (202), isi sonra bitirip BPM'e doner
                    String callback = req.get("callbackUrl").asText();
                    ex.sendResponseHeaders(202, -1);
                    ex.close();
                    data.putObject("sGK_sOUT").put("mf_sonuc", "OK");
                    CompletableFuture.runAsync(() -> mainframeGeriDonus(callback, data));
                    return;
                }
                // IH_DAB_TANZIM_FC: sExport_sALL/sExport_sWIP, EBR_BASIC_TEST: sGK_sALL/sGK_sWIP
                ObjectNode wip = (ObjectNode) (data.has("sGK_sWIP") ? data.get("sGK_sWIP") : data.get("sExport_sWIP"));
                if ("116093".equals(program) || "54866".equals(program)) {
                    wip.put("status_code", "0");
                } else {
                    wip.put("return_code", "00");
                }
                byte[] out = M.writeValueAsBytes(Map.of("data", data));
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, out.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(out);
                }
            });
            s.start();
            return s;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void mainframeGeriDonus(String callback, ObjectNode data) {
        try {
            Thread.sleep(300);
            HttpRequest r = HttpRequest.newBuilder(URI.create(callback))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("mainframe:mainframe".getBytes()))
                    .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(Map.of("data", data))))
                    .build();
            GERI_DONUS_HTTP.set(HttpClient.newHttpClient().send(r, HttpResponse.BodyHandlers.ofString()).statusCode());
        } catch (Exception e) {
            GERI_DONUS_HTTP.set(-1);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // handler System property'den okur (EnvironmentPostProcessor ile ayni yol)
        System.setProperty("avrasya.service.base-url", "http://127.0.0.1:" + AVRASYA.getAddress().getPort() + "/avrasya");
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:ihdabit;MVCC=TRUE;DB_CLOSE_DELAY=-1");
    }

    @AfterAll
    static void stop() {
        AVRASYA.stop(0);
        System.clearProperty("avrasya.service.base-url");
    }

    private TestRestTemplate as(String u) {
        return rest.withBasicAuth(u, u);
    }

    private void tamamla(String gorev, Map<String, Object> govde) {
        ResponseEntity<String> r = as("entegrasyon").postForEntity(
                "/api/ih-dab-tanzim-fc/surecler/1234/98765/IHR/7/gorevler/" + gorev + "/tamamla", govde, String.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), gorev + " -> " + r.getBody());
    }

    private JsonNode durum() throws Exception {
        ResponseEntity<String> r = as("entegrasyon").getForEntity("/api/ih-dab-tanzim-fc/surecler/1234/98765/IHR/7", String.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), r.getBody());
        return M.readTree(r.getBody());
    }

    @Test
    void uctanUcaRestIleSurec() throws Exception {
        CAGRILAR.clear();
        String govde = "{\"sExport_sWID\":{\"job_req_unit_num\":\"1234\",\"job_req_cust_num\":\"98765\","
                + "\"job_req_doc_code\":\"IHR\",\"job_req_seq_num\":\"7\"},"
                + "\"sExport_sQUE\":{\"qPortfoyAdi\":\"PRT\",\"qYetkiliOnay\":\"_YO\",\"qYarim\":\"_SB\",\"qIleriValor\":\"\"}}";
        ResponseEntity<String> start = as("entegrasyon").postForEntity("/api/ih-dab-tanzim-fc/surecler",
                M.readValue(govde, Map.class), String.class);
        assertEquals(HttpStatus.CREATED, start.getStatusCode(), start.getBody());

        JsonNode d = durum();
        assertEquals("ACTIVE", d.get("state").asText());
        assertEquals("Portfoy Belirleme1", d.get("aktifGorevler").get(0).get("name").asText());
        assertEquals("0", d.get("sExport_sALL").get("sExport_sWIP").get("status_code").asText(),
                "Referans servisi gercek HTTP handler ile cagrilmis olmali");

        tamamla("Portfoy Belirleme1", Map.of("kuyrukAdi", "PRT_GIRIS"));
        tamamla("DAB Tanzim Giris", Map.of("rework", "N", "action", "ONAY"));
        tamamla("DAB Tanzim Yetkili 2.Onay", Map.of("rework", "N"));
        tamamla("WC Bekleme", Map.of());
        tamamla("FIRCO", Map.of());
        tamamla("PROCESS", Map.of());

        assertEquals("COMPLETED", durum().get("state").asText());
        assertEquals(List.of("116093", "116092", "116092", "116095", "179348", "116095"),
                CAGRILAR.subList(CAGRILAR.size() - 6, CAGRILAR.size()));
    }

    @Test
    void ebrBasicTest_MainframeeGidipBpmeGeriDoner() throws Exception {
        CAGRILAR.clear();
        ResponseEntity<String> start = as("entegrasyon").postForEntity("/api/ebr-basic-test/surecler",
                Map.of("sGK_sWID", Map.of("musteri_no", "98765")), String.class);
        assertEquals(HttpStatus.CREATED, start.getStatusCode(), start.getBody());
        long pid = M.readTree(start.getBody()).get("processInstanceId").asLong();

        String state = "";
        for (int i = 0; i < 50 && !"COMPLETED".equals(state); i++) {
            Thread.sleep(200);
            state = M.readTree(as("entegrasyon").getForEntity("/api/ebr-basic-test/surecler/" + pid, String.class)
                    .getBody()).get("state").asText();
        }
        assertEquals(200, GERI_DONUS_HTTP.get(), "mainframe geri donusu kabul edilmeli");
        assertEquals("COMPLETED", state);
        assertEquals(List.of("54866", "269812"), CAGRILAR);
    }

    @Test
    void geriDonusUcuYalnizcaMainframeKullanicisina() {
        ResponseEntity<String> r = as("entegrasyon").postForEntity("/api/avrasya/geri-donus/1/1", Map.of("data", Map.of()), String.class);
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
    }

    @Test
    void bilinmeyenSurec404() {
        ResponseEntity<String> r = as("entegrasyon").getForEntity("/api/ih-dab-tanzim-fc/surecler/0/0/X/0", String.class);
        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertTrue(r.getBody().contains("bulunamadi"));
    }
}
