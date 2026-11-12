package com.garantibbva.processarchitecture.ihdabtanzimfc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.garantibbva.processarchitecture.ihdabtanzimfc.model.ExportAll;
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
 * IH_DAB_TANZIM_FC surecinin uctan uca testleri. Insan gorevleri gercek jBPM task
 * servisiyle, Avrasya servisleri {@link MockAvrasyaService} ile calisir.
 */
public class IhDabTanzimFcProcessTest extends JbpmJUnitBaseTestCase {

    private static final String DIR = "com/garantibbva/processarchitecture/ihdabtanzimfc/";
    private static final String USER = "ali";

    private KieSession ksession;
    private TaskService taskService;
    private RuntimeEngine engine;
    private MockAvrasyaService services;

    public IhDabTanzimFcProcessTest() {
        super(true, true);
    }

    @Before
    public void init() {
        createRuntimeManager(Strategy.SINGLETON, "ihDabTanzimFc",
                DIR + "IH_DAB_TANZIM_FC.bpmn",
                DIR + "IH_DAB_TANZIM_FC.ENCLAVE.bpmn",
                DIR + "IH_DAB_TANZIM_FC.SUBEYE_IADE.bpmn",
                DIR + "IH_DAB_TANZIM_FC.DAB_TANZIM_GIRIS.bpmn",
                DIR + "IH_DAB_TANZIM_FC.FIRCOSOFT.bpmn");
        engine = getRuntimeEngine();
        ksession = engine.getKieSession();
        taskService = engine.getTaskService();
        services = new MockAvrasyaService();
        ksession.getWorkItemManager().registerWorkItemHandler("AvrasyaService", services);
    }

    @After
    public void cleanup() {
        disposeRuntimeManager();
    }

    // =================================================================== senaryolar

    @Test
    public void mutluYol_GirisOnayWorldCheckFircoTemiz() {
        long pid = start(veri(""));

        complete(pid, "Portfoy Belirleme1", "WQ_587", out("kuyrukAdi", "PRT_GIRIS"));
        complete(pid, "DAB Tanzim Giris", "PRT_GIRIS", out("rework", "N", "action", "ONAY"));
        complete(pid, "DAB Tanzim Yetkili 2.Onay", "PRT_YO", out("rework", "N"));
        complete(pid, "WC Bekleme", "WQ_742", out());
        complete(pid, "FIRCO", "PRT", out());
        complete(pid, "PROCESS", "PRT", out());

        assertProcessInstanceCompleted(pid);
        assertEquals(Arrays.asList("116093", "116092", "116092", "116095", "179348", "116095"), services.calls);
        ExportAll son = services.last;
        assertEquals("O2", son.getPro().getHistApproval());
        assertEquals("PESINMAL", son.getPro().getCallerApplication());
        assertEquals("2", son.getPro().getApprovalCode());
        assertEquals("Y", son.getPro().getDisableFlag());
        assertTrue(activeTasks(pid).isEmpty());
    }

    @Test
    public void subeyeIadeSonraIptal() {
        long pid = start(veri(""));

        complete(pid, "Portfoy Belirleme1", "WQ_587", out("kuyrukAdi", "PRT_GIRIS"));
        complete(pid, "DAB Tanzim Giris", "PRT_GIRIS", out("rework", "N", "action", "IADE"));
        // ENCLAVE -> Subeye Iade -> DOKUMAN BEKLEME (kuyruk = qPortfoyAdi + qYarim)
        complete(pid, "DOKUMAN BEKLEME", "PRT_SB", out());
        // Iade sonrasi ENCLAVE dongusu: DAB Tanzim Giris blogu bastan
        complete(pid, "Portfoy Belirleme1", "WQ_587", out("kuyrukAdi", "PRT_GIRIS"));
        complete(pid, "DAB Tanzim Giris", "PRT_GIRIS", out("rework", "N", "action", "IPTAL"));

        assertProcessInstanceCompleted(pid);
        assertEquals(Arrays.asList("116093", "116094"), services.calls);
        assertEquals("", services.last.getNav().getRestartReq());
    }

    @Test
    public void yetkiliIade() {
        long pid = start(veri(""));
        complete(pid, "Portfoy Belirleme1", "WQ_587", out("kuyrukAdi", "PRT_GIRIS"));
        complete(pid, "DAB Tanzim Giris", "PRT_GIRIS", out("rework", "N"));
        complete(pid, "DAB Tanzim Yetkili 2.Onay", "PRT_YO", out("rework", "N", "action", "IADE"));
        TaskSummary t = waitTask(pid, "DOKUMAN BEKLEME");
        assertNotNull(t);
        assertProcessInstanceActive(pid);
    }

    @Test
    public void reworkNDegilseGorevTekrarAcilir() {
        long pid = start(veri(""));
        complete(pid, "Portfoy Belirleme1", "WQ_587", out("kuyrukAdi", "PRT_GIRIS"));
        long ilk = complete(pid, "DAB Tanzim Giris", "PRT_GIRIS", out("rework", "Y"));
        TaskSummary tekrar = waitTask(pid, "DAB Tanzim Giris");
        assertTrue("yeni gorev olusmali", tekrar.getId() != ilk);
        complete(pid, "DAB Tanzim Giris", "PRT_GIRIS", out("rework", "N"));
        assertNotNull(waitTask(pid, "DAB Tanzim Yetkili 2.Onay"));
    }

    @Test
    public void portfoyBosIseTekrarSorulur_ErtesiGun() {
        long pid = start(veri(""));
        complete(pid, "Portfoy Belirleme1", "WQ_587", out());
        complete(pid, "Portfoy Belirleme1", "WQ_587", out("kuyrukAdi", "PRT_GIRIS", "action", "ERTESI_GUN"));
        // bitis kosulu saglanmazsa (return_code != 00) gorev tekrar acilir
        complete(pid, "ErtesiGun", "PRT_GIRIS", out("returnCode", "01"));
        complete(pid, "ErtesiGun", "PRT_GIRIS", out());
        assertNotNull(waitTask(pid, "DAB Tanzim Giris"));
    }

    @Test
    public void ileriValorTimeoutVeUyum() {
        long pid = start(veri("_IV"));
        complete(pid, "Portfoy Belirleme1", "WQ_587", out("kuyrukAdi", "PRT_GIRIS"));
        complete(pid, "DAB Tanzim Giris", "PRT_GIRIS", out("rework", "N"));
        complete(pid, "DAB Tanzim Yetkili 2.Onay", "PRT_YO", out("rework", "N"));
        complete(pid, "WC Bekleme", "WQ_742", out());
        complete(pid, "TIMEOUT", "WQ_1361", out());
        complete(pid, "FIRCO", "PRT_IV", out("action", "UYUM"));
        complete(pid, "UYUM", "WQ_UYUM", out("action", "ONAY"));
        complete(pid, "PROCESS", "PRT_IV", out());
        assertProcessInstanceCompleted(pid);
    }

    @Test
    public void fircoUyumIptal() {
        long pid = start(veri(""));
        complete(pid, "Portfoy Belirleme1", "WQ_587", out("kuyrukAdi", "PRT_GIRIS"));
        complete(pid, "DAB Tanzim Giris", "PRT_GIRIS", out("rework", "N"));
        complete(pid, "DAB Tanzim Yetkili 2.Onay", "PRT_YO", out("rework", "N"));
        complete(pid, "WC Bekleme", "WQ_742", out());
        complete(pid, "FIRCO", "PRT", out("action", "UYUM"));
        complete(pid, "UYUM", "WQ_UYUM", out("action", "IPTAL"));
        assertProcessInstanceCompleted(pid);
        assertEquals("116094", services.calls.get(services.calls.size() - 1));
    }

    @Test
    public void servisHatasi_HataKuyrugu_TekrarDene() {
        services.failNext("116093", 1);
        long pid = start(veri(""));
        TaskSummary hata = waitTask(pid, "Referans Process Aciklama Ekle - Hata Kuyrugu");
        assertNotNull(hata);
        assertTrue(activeTaskNames(pid).size() == 1);
        complete(pid, "Referans Process Aciklama Ekle - Hata Kuyrugu", "WQ_585", out());
        assertNotNull(waitTask(pid, "Portfoy Belirleme1"));
        assertEquals(Arrays.asList("116093", "116093"), services.calls);
    }

    @Test
    public void servisHatasi_HataKuyrugu_Atla() {
        services.failNext("179348", 5);
        long pid = start(veri(""));
        complete(pid, "Portfoy Belirleme1", "WQ_587", out("kuyrukAdi", "PRT_GIRIS"));
        complete(pid, "DAB Tanzim Giris", "PRT_GIRIS", out("rework", "N"));
        complete(pid, "DAB Tanzim Yetkili 2.Onay", "PRT_YO", out("rework", "N"));
        complete(pid, "WC Bekleme", "WQ_742", out());
        complete(pid, "WC Control - Hata Kuyrugu", "WQ_585", out("karar", "ATLA"));
        assertNotNull(waitTask(pid, "FIRCO"));
    }

    // =================================================================== yardimcilar

    private static ExportAll veri(String ileriValor) {
        ExportAll e = new ExportAll();
        e.getWid().setJobReqUnitNum("1234");
        e.getWid().setJobReqCustNum("98765");
        e.getWid().setJobReqDocCode("IHR");
        e.getWid().setJobReqSeqNum("1");
        e.getQue().setQPortfoyAdi("PRT");
        e.getQue().setQYetkiliOnay("_YO");
        e.getQue().setQYarim("_SB");
        e.getQue().setQIleriValor(ileriValor);
        return e;
    }

    private long start(ExportAll e) {
        Map<String, Object> params = new HashMap<>();
        params.put("exportAll", e);
        ProcessInstance pi = ksession.startProcess("IH_DAB_TANZIM_FC", params);
        return pi.getId();
    }

    private static Map<String, Object> out(String... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /** pid ve tum alt surec instance'lari. */
    private Set<Long> family(long pid) {
        Set<Long> ids = new HashSet<>();
        List<Long> todo = new ArrayList<>(Collections.singletonList(pid));
        while (!todo.isEmpty()) {
            long id = todo.remove(0);
            ids.add(id);
            for (org.kie.api.runtime.manager.audit.ProcessInstanceLog l : engine.getAuditService().findSubProcessInstances(id)) {
                todo.add(l.getProcessInstanceId());
            }
        }
        return ids;
    }

    private List<TaskSummary> activeTasks(long pid) {
        Set<Long> ids = family(pid);
        List<TaskSummary> res = new ArrayList<>();
        for (TaskSummary t : taskService.getTasksAssignedAsPotentialOwner(USER, "en-UK")) {
            if (ids.contains(t.getProcessInstanceId())) {
                res.add(t);
            }
        }
        return res;
    }

    private List<String> activeTaskNames(long pid) {
        List<String> n = new ArrayList<>();
        activeTasks(pid).forEach(t -> n.add(t.getName()));
        return n;
    }

    private TaskSummary waitTask(long pid, String name) {
        for (TaskSummary t : activeTasks(pid)) {
            if (t.getName().equals(name)) {
                return t;
            }
        }
        fail("Aktif gorev bulunamadi: '" + name + "', aktif: " + activeTaskNames(pid));
        return null;
    }

    private long complete(long pid, String name, String expectedGroup, Map<String, Object> outputs) {
        TaskSummary t = waitTask(pid, name);
        List<String> groups = new ArrayList<>();
        taskService.getTaskById(t.getId()).getPeopleAssignments().getPotentialOwners()
                .forEach(o -> groups.add(o.getId()));
        assertEquals("'" + name + "' gorevinin kuyrugu", Collections.singletonList(expectedGroup), groups);
        taskService.claim(t.getId(), USER);
        taskService.start(t.getId(), USER);
        taskService.complete(t.getId(), USER, outputs);
        return t.getId();
    }

    /** Avrasya servislerinin mock'u: basariyla doner, istenirse hata uretir. */
    static class MockAvrasyaService implements WorkItemHandler {
        final List<String> calls = new ArrayList<>();
        final Map<String, Integer> failures = new HashMap<>();
        ExportAll last;

        void failNext(String program, int times) {
            failures.put(program, times);
        }

        @Override
        public void executeWorkItem(WorkItem wi, WorkItemManager manager) {
            String program = (String) wi.getParameter("ProgramID");
            ExportAll e = (ExportAll) wi.getParameter("Data");
            assertNotNull("Data parametresi", e);
            assertFalse("servis oncesi return_code temizlenmeli", "00".equals(e.getWip().getReturnCode()));
            calls.add(program);
            Map<String, Object> res = new HashMap<>();
            int f = failures.getOrDefault(program, 0);
            if (f > 0) {
                failures.put(program, f - 1);
                res.put("ServiceError", "mock hata");
            } else if ("116093".equals(program)) {
                e.getWip().setStatusCode("0");
            } else {
                e.getWip().setReturnCode("00");
            }
            res.put("Data", e);
            last = e;
            manager.completeWorkItem(wi.getId(), res);
        }

        @Override
        public void abortWorkItem(WorkItem wi, WorkItemManager manager) {
        }
    }
}
