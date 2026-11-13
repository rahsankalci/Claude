package com.garantibbva.processarchitecture.ihdabtanzimfc.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;

public class AvrasyaServiceWorkItemHandlerTest {

    /** Test konteyneri - gercek model kjar'da, handler tipten bagimsiz calisir. */
    public static class Box {
        public String status;
        public String note;
    }

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "{\"data\":{\"status\":\"00\",\"note\":\"islendi\"}}";

    @Before
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/avrasya/", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @After
    public void stop() {
        server.stop(0);
    }

    private final Map<String, String> extra = new HashMap<>();

    private AvrasyaServiceWorkItemHandler handler() {
        Map<String, String> cfg = new HashMap<>(extra);
        cfg.put("avrasya.service.base-url", "http://127.0.0.1:" + server.getAddress().getPort() + "/avrasya");
        cfg.put("avrasya.service.auth-header", "Bearer test");
        return new AvrasyaServiceWorkItemHandler(new AvrasyaServiceConfig(cfg::get));
    }

    private static WorkItem workItem(Map<String, Object> params) {
        return (WorkItem) Proxy.newProxyInstance(WorkItem.class.getClassLoader(), new Class<?>[]{WorkItem.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getId": return 7L;
                        case "getProcessInstanceId": return 42L;
                        case "getName": return AvrasyaServiceWorkItemHandler.NAME;
                        case "getParameter": return params.get((String) a[0]);
                        case "getParameters": return params;
                        default: return null;
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private static WorkItemManager manager(AtomicReference<Map<String, Object>> results) {
        return (WorkItemManager) Proxy.newProxyInstance(WorkItemManager.class.getClassLoader(),
                new Class<?>[]{WorkItemManager.class}, (p, m, a) -> {
                    if (m.getName().equals("completeWorkItem")) {
                        results.set((Map<String, Object>) a[1]);
                    }
                    return null;
                });
    }

    private Map<String, Object> run(Box box) {
        return run(box, true);
    }

    private Map<String, Object> run(Box box, boolean mustComplete) {
        Map<String, Object> params = new HashMap<>();
        params.put("ProgramID", "116093");
        params.put("ActivityName", "Referans Process Aciklama Ekle");
        params.put("Data", box);
        AtomicReference<Map<String, Object>> results = new AtomicReference<>();
        handler().executeWorkItem(workItem(params), manager(results));
        if (mustComplete) {
            assertNotNull("work item tamamlanmali", results.get());
        }
        return results.get();
    }

    @Test
    public void basariliCagriKonteyneriGunceller() {
        Box in = new Box();
        in.status = "";
        Map<String, Object> r = run(in);
        assertNull(r.get("ServiceError"));
        Box out = (Box) r.get("Data");
        assertEquals("00", out.status);
        assertEquals("islendi", out.note);
        assertTrue(lastBody.get().contains("\"programId\":\"116093\""));
        assertTrue(lastBody.get().contains("\"processInstanceId\":42"));
        assertEquals("Bearer test", lastAuth.get());
    }

    @Test
    public void duzKonteynerYanitiDaKabulEdilir() {
        reply = "{\"status\":\"00\"}";
        Box out = (Box) run(new Box()).get("Data");
        assertEquals("00", out.status);
    }

    @Test
    public void httpHatasiIstisnaFirlatmazServiceErrorDoner() {
        status = 500;
        reply = "sunucu hatasi";
        Box in = new Box();
        Map<String, Object> r = run(in);
        assertSame(in, r.get("Data"));
        assertTrue(((String) r.get("ServiceError")).contains("HTTP 500"));
    }

    @Test
    public void tanimsizProgramServiceErrorDoner() {
        AvrasyaServiceConfig empty = new AvrasyaServiceConfig(k -> null);
        Map<String, Object> params = new HashMap<>();
        params.put("ProgramID", "999");
        params.put("Data", new Box());
        AtomicReference<Map<String, Object>> results = new AtomicReference<>();
        new AvrasyaServiceWorkItemHandler(empty).executeWorkItem(workItem(params), manager(results));
        assertTrue(((String) results.get().get("ServiceError")).contains("999"));
    }

    @Test
    public void http202MainframeAsenkron_WorkItemAcikKalir() {
        status = 202;
        reply = "";
        extra.put("avrasya.service.callback-url", "http://bpm:8090/api/avrasya/geri-donus");
        assertNull("asenkron kabulde work item tamamlanmamali", run(new Box(), false));
        assertTrue(lastBody.get().contains("\"callbackUrl\":\"http://bpm:8090/api/avrasya/geri-donus/42/7\""));
    }

    @Test
    public void asyncModAyariIle200deDeBekler() {
        extra.put("avrasya.service.program.116093.mode", "async");
        assertNull(run(new Box(), false));
    }

    @Test
    public void ortamDegiskeniAdlari() {
        assertEquals("AVRASYA_SERVICE_PROGRAM_116093_URL",
                AvrasyaServiceConfig.toEnvName("avrasya.service.program.116093.url"));
        assertEquals("AVRASYA_SERVICE_BASE_URL", AvrasyaServiceConfig.toEnvName("avrasya.service.base-url"));
    }
}
