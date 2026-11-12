package com.garantibbva.processarchitecture.ihdabtanzimfc.service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jbpm.services.api.DeploymentService;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.UserTaskService;
import org.jbpm.services.api.model.ProcessDefinition;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.query.QueryContext;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.KieInternalServices;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.query.QueryFilter;
import org.kie.internal.runtime.manager.InternalRuntimeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IH_DAB_TANZIM_FC entegrasyon uclari. Surec instance'lari, Avrasya'daki is istegi
 * kimligi (sube | musteri | islem tipi | sira no) ile KIE correlation key olarak eslenir;
 * boylece dis sistemler process instance id bilmeden calisabilir.
 *
 * <pre>
 * POST /api/ih-dab-tanzim-fc/surecler
 *      govde: sExport_sALL JSON (Avrasya dugum adlariyla)            -> sureci baslatir
 * GET  /api/ih-dab-tanzim-fc/surecler/{sube}/{musteri}/{tip}/{sira}  -> durum + aktif gorevler + konteyner
 * POST /api/ih-dab-tanzim-fc/surecler/{sube}/{musteri}/{tip}/{sira}/gorevler/{gorevAdi}/tamamla
 *      govde: {"action":"..","rework":"N","kuyrukAdi":"..","returnCode":"00"} (hepsi opsiyonel)
 *      -> bekleyen gorevi (FIRCO, WC Bekleme, TIMEOUT, DOKUMAN BEKLEME, ...) tamamlar
 * </pre>
 * Gorevi tamamlayan kullanici, istegi yapan (kimligi dogrulanmis) kullanicidir ve gorevin
 * kuyruk grubunda olmalidir.
 */
@RestController
@RequestMapping("/api/ih-dab-tanzim-fc")
public class SurecEntegrasyonController {

    static final String PROCESS_ID = "IH_DAB_TANZIM_FC";
    static final String MODEL_CLASS = "com.garantibbva.processarchitecture.ihdabtanzimfc.model.ExportAll";
    private static final List<Status> AKTIF = Arrays.asList(Status.Created, Status.Ready, Status.Reserved, Status.InProgress);
    private static final List<String> GOREV_CIKTILARI = Arrays.asList("action", "rework", "kuyrukAdi", "returnCode", "karar");

    private static final Logger log = LoggerFactory.getLogger(SurecEntegrasyonController.class);

    private final ProcessService processService;
    private final RuntimeDataService runtimeDataService;
    private final UserTaskService userTaskService;
    private final DeploymentService deploymentService;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public SurecEntegrasyonController(ProcessService processService, RuntimeDataService runtimeDataService,
                                      UserTaskService userTaskService, DeploymentService deploymentService) {
        this.processService = processService;
        this.runtimeDataService = runtimeDataService;
        this.userTaskService = userTaskService;
        this.deploymentService = deploymentService;
    }

    // ------------------------------------------------------------------ baslat

    @PostMapping("/surecler")
    public ResponseEntity<Map<String, Object>> baslat(@RequestBody Map<String, Object> exportAll) {
        String deploymentId = deploymentId();
        Object data = toModel(deploymentId, exportAll);
        List<String> key = anahtar(exportAll);
        CorrelationKey ck = correlationKey(key);

        Map<String, Object> params = new HashMap<>();
        params.put("exportAll", data);
        Long pid = processService.startProcess(deploymentId, PROCESS_ID, ck, params);
        log.info("IH_DAB_TANZIM_FC baslatildi :: pid={}, anahtar={}", pid, ck.toExternalForm());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("processInstanceId", pid);
        res.put("correlationKey", ck.toExternalForm());
        res.put("deploymentId", deploymentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    // ------------------------------------------------------------------ durum

    @GetMapping("/surecler/{sube}/{musteri}/{tip}/{sira}")
    public Map<String, Object> durum(@PathVariable String sube, @PathVariable String musteri,
                                     @PathVariable String tip, @PathVariable String sira) {
        ProcessInstanceDesc pi = bul(sube, musteri, tip, sira);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("processInstanceId", pi.getId());
        res.put("state", durumAdi(pi.getState()));
        res.put("correlationKey", pi.getCorrelationKey());
        List<Map<String, Object>> gorevler = new ArrayList<>();
        for (TaskSummary t : aktifGorevler(pi.getId())) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("taskId", t.getId());
            g.put("name", t.getName());
            g.put("status", t.getStatus().name());
            g.put("processInstanceId", t.getProcessInstanceId());
            g.put("processId", t.getProcessId());
            g.put("createdOn", t.getCreatedOn());
            gorevler.add(g);
        }
        res.put("aktifGorevler", gorevler);
        if (pi.getState() == ProcessInstance.STATE_ACTIVE) {
            Object e = processService.getProcessInstanceVariables(pi.getDeploymentId(), pi.getId()).get("exportAll");
            res.put("sExport_sALL", e == null ? null : mapper.convertValue(e, Map.class));
        }
        return res;
    }

    // ------------------------------------------------------------------ gorev tamamla

    @PostMapping("/surecler/{sube}/{musteri}/{tip}/{sira}/gorevler/{gorevAdi}/tamamla")
    public Map<String, Object> gorevTamamla(@PathVariable String sube, @PathVariable String musteri,
                                            @PathVariable String tip, @PathVariable String sira,
                                            @PathVariable String gorevAdi,
                                            @RequestBody(required = false) Map<String, Object> govde,
                                            Principal principal) {
        ProcessInstanceDesc pi = bul(sube, musteri, tip, sira);
        TaskSummary gorev = aktifGorevler(pi.getId()).stream()
                .filter(t -> t.getName().equalsIgnoreCase(gorevAdi))
                .min(Comparator.comparing(TaskSummary::getId))
                .orElseThrow(() -> new BulunamadiException("Aktif '" + gorevAdi + "' gorevi yok (pid " + pi.getId() + ")"));

        Map<String, Object> cikti = new HashMap<>();
        if (govde != null) {
            for (String k : GOREV_CIKTILARI) {
                if (govde.get(k) != null) {
                    cikti.put(k, String.valueOf(govde.get(k)));
                }
            }
        }
        userTaskService.completeAutoProgress(gorev.getId(), principal.getName(), cikti);
        log.info("Gorev tamamlandi :: pid={}, gorev={}({}), kullanici={}, cikti={}",
                pi.getId(), gorev.getName(), gorev.getId(), principal.getName(), cikti);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("taskId", gorev.getId());
        res.put("name", gorev.getName());
        res.put("tamamlandi", true);
        return res;
    }

    // ------------------------------------------------------------------ yardimcilar

    private String deploymentId() {
        Collection<ProcessDefinition> defs = runtimeDataService.getProcessesById(PROCESS_ID);
        return defs.stream()
                .filter(d -> deploymentService.isDeployed(d.getDeploymentId()))
                .map(ProcessDefinition::getDeploymentId)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException(PROCESS_ID + " sureci deploy edilmemis"));
    }

    /** Gelen JSON'u kjar'in kendi class loader'indaki ExportAll tipine cevirir. */
    private Object toModel(String deploymentId, Map<String, Object> json) {
        try {
            ClassLoader cl = ((InternalRuntimeManager) deploymentService.getDeployedUnit(deploymentId).getRuntimeManager())
                    .getEnvironment().getClassLoader();
            Class<?> type = Class.forName(MODEL_CLASS, true, cl);
            Object model = mapper.convertValue(json, type);
            type.getMethod("ensureInitialized").invoke(model);
            return model;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Veri modeli yuklenemedi: " + MODEL_CLASS, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> anahtar(Map<String, Object> exportAll) {
        Object wid = exportAll.get("sExport_sWID");
        if (!(wid instanceof Map)) {
            throw new GecersizIstekException("sExport_sWID zorunlu");
        }
        Map<String, Object> w = (Map<String, Object>) wid;
        List<String> key = new ArrayList<>();
        for (String f : Arrays.asList("job_req_unit_num", "job_req_cust_num", "job_req_doc_code", "job_req_seq_num")) {
            Object v = w.get(f);
            if (v == null || String.valueOf(v).isBlank()) {
                throw new GecersizIstekException("sExport_sWID/" + f + " zorunlu");
            }
            key.add(String.valueOf(v));
        }
        return key;
    }

    private static CorrelationKey correlationKey(List<String> parts) {
        return KieInternalServices.Factory.get().newCorrelationKeyFactory().newCorrelationKey(parts);
    }

    private ProcessInstanceDesc bul(String sube, String musteri, String tip, String sira) {
        CorrelationKey ck = correlationKey(Arrays.asList(sube, musteri, tip, sira));
        ProcessInstanceDesc pi = runtimeDataService.getProcessInstanceByCorrelationKey(ck); // yalnizca aktif
        if (pi != null) {
            return pi;
        }
        // tamamlanmis / iptal edilmis instance'lar: audit kaydindan en yenisi (tam esleme)
        String key = ck.toExternalForm();
        return runtimeDataService.getProcessInstancesByCorrelationKey(ck, new QueryContext(0, 100)).stream()
                .filter(p -> key.equals(p.getCorrelationKey()))
                .max(Comparator.comparing(ProcessInstanceDesc::getId))
                .orElseThrow(() -> new BulunamadiException("Surec bulunamadi: " + key));
    }

    /** Ana surec ve tum alt surecleri (ENCLAVE, DAB Tanzim Giris, Fircosoft, ...) icin aktif gorevler. */
    private List<TaskSummary> aktifGorevler(long rootPid) {
        List<Long> pids = new ArrayList<>();
        pids.add(rootPid);
        for (int i = 0; i < pids.size(); i++) {
            for (ProcessInstanceDesc c : runtimeDataService.getProcessInstancesByParent(pids.get(i),
                    Arrays.asList(ProcessInstance.STATE_ACTIVE), new QueryContext(0, 100))) {
                pids.add(c.getId());
            }
        }
        List<TaskSummary> res = new ArrayList<>();
        for (Long pid : pids) {
            res.addAll(runtimeDataService.getTasksByStatusByProcessInstanceId(pid, AKTIF, new QueryFilter(0, 100)));
        }
        return res;
    }

    private static String durumAdi(int state) {
        switch (state) {
            case ProcessInstance.STATE_PENDING: return "PENDING";
            case ProcessInstance.STATE_ACTIVE: return "ACTIVE";
            case ProcessInstance.STATE_COMPLETED: return "COMPLETED";
            case ProcessInstance.STATE_ABORTED: return "ABORTED";
            case ProcessInstance.STATE_SUSPENDED: return "SUSPENDED";
            default: return String.valueOf(state);
        }
    }

    static class BulunamadiException extends RuntimeException {
        BulunamadiException(String m) {
            super(m);
        }
    }

    static class GecersizIstekException extends RuntimeException {
        GecersizIstekException(String m) {
            super(m);
        }
    }

    @ExceptionHandler(BulunamadiException.class)
    ResponseEntity<Map<String, String>> bulunamadi(BulunamadiException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("hata", e.getMessage()));
    }

    @ExceptionHandler(GecersizIstekException.class)
    ResponseEntity<Map<String, String>> gecersiz(GecersizIstekException e) {
        return ResponseEntity.badRequest().body(Map.of("hata", e.getMessage()));
    }
}
