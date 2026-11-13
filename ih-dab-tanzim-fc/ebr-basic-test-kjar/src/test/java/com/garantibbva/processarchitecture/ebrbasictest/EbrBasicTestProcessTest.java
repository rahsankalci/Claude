package com.garantibbva.processarchitecture.ebrbasictest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.garantibbva.processarchitecture.ebrbasictest.model.GkAll;
import org.jbpm.test.JbpmJUnitBaseTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.TaskSummary;

/**
 * EBR_BASIC_TEST: Referans (senkron) -> WebClient (mainframe'e gider, geri donus beklenir) -> End.
 * Mainframe'in BPM'e geri donusu, acik work item'in tamamlanmasiyla simule edilir
 * (uretimde bunu /api/avrasya/geri-donus ucu yapar).
 */
public class EbrBasicTestProcessTest extends JbpmJUnitBaseTestCase {

    private static final String BPMN = "com/garantibbva/processarchitecture/ebrbasictest/EBR_BASIC_TEST.bpmn";

    private KieSession ksession;
    private TaskService taskService;
    private Mainframe mainframe;

    public EbrBasicTestProcessTest() {
        super(true, true);
    }

    @Before
    public void init() {
        createRuntimeManager(Strategy.SINGLETON, "ebrBasicTest", BPMN);
        RuntimeEngine engine = getRuntimeEngine();
        ksession = engine.getKieSession();
        taskService = engine.getTaskService();
        mainframe = new Mainframe();
        ksession.getWorkItemManager().registerWorkItemHandler("AvrasyaService", mainframe);
    }

    @After
    public void cleanup() {
        disposeRuntimeManager();
    }

    private long start() {
        GkAll gk = new GkAll();
        gk.setDigerAlan("sGK_sWID", new HashMap<>(Map.of("musteri_no", "98765")));
        Map<String, Object> p = new HashMap<>();
        p.put("gkAll", gk);
        return ksession.startProcess("EBR_BASIC_TEST", p).getId();
    }

    @Test
    public void webClientMainframeGeriDonusuIleTamamlanir() {
        long pid = start();
        assertEquals(Arrays.asList("54866", "269812"), mainframe.calls);
        // WebClient mainframe'e gitti; surec geri donusu bekliyor
        assertProcessInstanceActive(pid);
        assertNodeActive(pid, ksession, "WebClient");
        assertTrue(taskService.getTasksAssignedAsPotentialOwner("ali", "en-UK").isEmpty());

        // mainframe -> BPM geri donus
        GkAll donen = (GkAll) mainframe.bekleyen.getParameter("Data");
        donen.setDigerAlan("sGK_sOUT", Map.of("sonuc", "OK"));
        Map<String, Object> res = new HashMap<>();
        res.put("Data", donen);
        res.put("ServiceError", null);
        ksession.getWorkItemManager().completeWorkItem(mainframe.bekleyen.getId(), res);

        assertProcessInstanceCompleted(pid);
        assertNodeTriggered(pid, "Start", "Referans Process Aciklama Ekle", "WebClient", "End");
    }

    @Test
    public void mainframeHataDonerseWq1131HataKuyrugu_TekrarDene() {
        long pid = start();
        Map<String, Object> res = new HashMap<>();
        res.put("Data", mainframe.bekleyen.getParameter("Data"));
        res.put("ServiceError", "MF: CICS ABEND AEI0");
        ksession.getWorkItemManager().completeWorkItem(mainframe.bekleyen.getId(), res);

        TaskSummary t = tek("WebClient - Hata Kuyrugu");
        assertEquals("WQ_1131", taskService.getTaskById(t.getId()).getPeopleAssignments()
                .getPotentialOwners().get(0).getId());
        taskService.claim(t.getId(), "ali");
        taskService.start(t.getId(), "ali");
        taskService.complete(t.getId(), "ali", new HashMap<>()); // varsayilan: Tekrar Dene

        assertEquals("WebClient yeniden mainframe'e gitmeli", Arrays.asList("54866", "269812", "269812"), mainframe.calls);
        Map<String, Object> ok = new HashMap<>();
        ok.put("Data", mainframe.bekleyen.getParameter("Data"));
        ksession.getWorkItemManager().completeWorkItem(mainframe.bekleyen.getId(), ok);
        assertProcessInstanceCompleted(pid);
    }

    @Test
    public void referansStatusSifirDegilseWq585() {
        mainframe.referansStatus = "8";
        long pid = start();
        TaskSummary t = tek("Referans Process Aciklama Ekle - Hata Kuyrugu");
        assertEquals("WQ_585", taskService.getTaskById(t.getId()).getPeopleAssignments()
                .getPotentialOwners().get(0).getId());
        mainframe.referansStatus = "0";
        taskService.claim(t.getId(), "ali");
        taskService.start(t.getId(), "ali");
        taskService.complete(t.getId(), "ali", new HashMap<>());
        assertNotNull(mainframe.bekleyen);
        assertProcessInstanceActive(pid);
    }

    private TaskSummary tek(String ad) {
        List<TaskSummary> l = new ArrayList<>();
        for (TaskSummary t : taskService.getTasksAssignedAsPotentialOwner("ali", "en-UK")) {
            if (t.getName().equals(ad)) l.add(t);
        }
        assertEquals(ad, 1, l.size());
        return l.get(0);
    }

    /** 54866 senkron cevap verir; 269812 (WebClient) mainframe'e gider ve acik kalir. */
    static class Mainframe implements WorkItemHandler {
        final List<String> calls = new ArrayList<>();
        WorkItem bekleyen;
        String referansStatus = "0";

        @Override
        public void executeWorkItem(WorkItem wi, WorkItemManager m) {
            String prog = (String) wi.getParameter("ProgramID");
            calls.add(prog);
            GkAll e = (GkAll) wi.getParameter("Data");
            if ("54866".equals(prog)) {
                e.getWip().setStatusCode(referansStatus);
                Map<String, Object> r = new HashMap<>();
                r.put("Data", e);
                m.completeWorkItem(wi.getId(), r);
            } else {
                bekleyen = wi; // asenkron: geri donus bekleniyor
            }
        }

        @Override
        public void abortWorkItem(WorkItem wi, WorkItemManager m) {
        }
    }
}
