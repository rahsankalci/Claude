package @@pkg@@.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * @@processName@@ entegrasyon uclari (donusturucu tarafindan uretildi).
 *
 * <p>Surec instance'i, Avrasya'daki surec anahtari ile KIE correlation key olarak eslenir:
 * @@keyDoc@@
 * <pre>
 * POST /api/@@slug@@/surecler                         govde: konteyner JSON  -> sureci baslatir
 * GET  /api/@@slug@@/surecler/{ref}                   durum + aktif gorevler + konteyner
 * POST /api/@@slug@@/surecler/{ref}/gorevler/{gorevAdi}/tamamla
 *      govde: {"values": {"sExport_sALL/sExport_sNAV/rework": "N", ...}}   (yol -> deger, istege bagli)
 * </pre>
 * {ref}: {@code a:b:c:d} (anahtar degerleri ':' ile) ya da {@code pid-123} (process instance id).
 * Gorevi tamamlayan kullanici, gorevin kuyruk grubunda (WQ_...) olmalidir.
 */
@RestController
@RequestMapping("/api/@@slug@@")
public class SurecEntegrasyonController {

    static final String PROCESS_ID = "@@processId@@";
    static final String MODEL_CLASS = "@@pkg@@.model.AvrasyaContainer";
    static final String CONTAINER_VAR = "container";
    /** Surec anahtarini olusturan konteyner yollari (Avrasya ProcessInfo/Attributes). */
    static final List<String> KEY_PATHS = Arrays.asList(@@keyPaths@@);
    private static final List<Status> AKTIF = Arrays.asList(Status.Created, Status.Ready, Status.Reserved, Status.InProgress);

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
    public ResponseEntity<Map<String, Object>> baslat(@RequestBody Map<String, Object> konteyner) {
        String deploymentId = deploymentId();
        Object data = toModel(deploymentId, konteyner);
        CorrelationKey ck = correlationKey(anahtar(konteyner));

        Map<String, Object> params = new HashMap<>();
        params.put(CONTAINER_VAR, data);
        Long pid = processService.startProcess(deploymentId, PROCESS_ID, ck, params);
        log.info("{} baslatildi :: pid={}, anahtar={}", PROCESS_ID, pid, ck.toExternalForm());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("processInstanceId", pid);
        res.put("correlationKey", ck.toExternalForm());
        res.put("deploymentId", deploymentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    // ------------------------------------------------------------------ durum

    @GetMapping("/surecler/{ref}")
    @SuppressWarnings("unchecked")
    public Map<String, Object> durum(@PathVariable String ref) {
        ProcessInstanceDesc pi = bul(ref);
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
            Object e = processService.getProcessInstanceVariables(pi.getDeploymentId(), pi.getId()).get(CONTAINER_VAR);
            res.put("konteyner", e == null ? null : mapper.convertValue(e, Map.class));
        }
        return res;
    }

    // ------------------------------------------------------------------ gorev tamamla

    @PostMapping("/surecler/{ref}/gorevler/{gorevAdi}/tamamla")
    @SuppressWarnings("unchecked")
    public Map<String, Object> gorevTamamla(@PathVariable String ref, @PathVariable String gorevAdi,
                                            @RequestBody(required = false) Map<String, Object> govde,
                                            Principal principal) {
        ProcessInstanceDesc pi = bul(ref);
        TaskSummary gorev = aktifGorevler(pi.getId()).stream()
                .filter(t -> t.getName().equalsIgnoreCase(gorevAdi))
                .min(Comparator.comparing(TaskSummary::getId))
                .orElseThrow(() -> new BulunamadiException("Aktif '" + gorevAdi + "' gorevi yok (pid " + pi.getId() + ")"));

        // Gorevin bagli oldugu (alt) surecteki konteyner + istekteki degerler -> gorev ciktisi "Data"
        String dep = gorev.getDeploymentId() != null ? gorev.getDeploymentId() : pi.getDeploymentId();
        Object mevcut = processService.getProcessInstanceVariables(dep, gorev.getProcessInstanceId()).get(CONTAINER_VAR);
        Map<String, Object> konteyner = mevcut == null ? new LinkedHashMap<>() : mapper.convertValue(mevcut, Map.class);
        if (govde != null && govde.get("values") instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) govde.get("values")).entrySet()) {
                yolaYaz(konteyner, e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
        }
        Map<String, Object> cikti = new HashMap<>();
        cikti.put("Data", toModel(dep, konteyner));
        userTaskService.completeAutoProgress(gorev.getId(), principal.getName(), cikti);
        log.info("Gorev tamamlandi :: pid={}, gorev={}({}), kullanici={}", pi.getId(), gorev.getName(), gorev.getId(),
                principal.getName());

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

    /** Gelen JSON'u kjar'in kendi class loader'indaki AvrasyaContainer tipine cevirir. */
    private Object toModel(String deploymentId, Map<String, Object> json) {
        try {
            ClassLoader cl = ((InternalRuntimeManager) deploymentService.getDeployedUnit(deploymentId).getRuntimeManager())
                    .getEnvironment().getClassLoader();
            return mapper.convertValue(json, Class.forName(MODEL_CLASS, true, cl));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Veri modeli yuklenemedi: " + MODEL_CLASS, e);
        }
    }

    /** Konteyner yolunu (kok adi olsa da olmasa da) ic ice Map'e yazar. */
    @SuppressWarnings("unchecked")
    static void yolaYaz(Map<String, Object> kok, String yol, String deger) {
        String p = yol.trim();
        int ilk = p.indexOf('/');
        // ilk parca konteyner kok adi ise (sXxx_sALL) atilir
        if (ilk > 0 && p.substring(0, ilk).equals("@@containerRoot@@")) {
            p = p.substring(ilk + 1);
        }
        String[] segs = p.split("/");
        Map<String, Object> cur = kok;
        for (int i = 0; i < segs.length - 1; i++) {
            Object n = cur.get(segs[i]);
            if (!(n instanceof Map)) {
                n = new LinkedHashMap<String, Object>();
                cur.put(segs[i], n);
            }
            cur = (Map<String, Object>) n;
        }
        cur.put(segs[segs.length - 1], deger);
    }

    @SuppressWarnings("unchecked")
    private static Object yoldanOku(Map<String, Object> kok, String yol) {
        String p = yol.trim();
        int ilk = p.indexOf('/');
        if (ilk > 0 && p.substring(0, ilk).equals("@@containerRoot@@")) {
            p = p.substring(ilk + 1);
        }
        Object cur = kok;
        for (String s : p.split("/")) {
            if (!(cur instanceof Map)) {
                return null;
            }
            cur = ((Map<String, Object>) cur).get(s);
        }
        return cur;
    }

    private static List<String> anahtar(Map<String, Object> konteyner) {
        if (KEY_PATHS.isEmpty()) {
            return List.of(UUID.randomUUID().toString());
        }
        List<String> key = new ArrayList<>();
        for (String path : KEY_PATHS) {
            Object v = yoldanOku(konteyner, path);
            if (v == null || String.valueOf(v).isBlank()) {
                throw new GecersizIstekException(path + " zorunlu");
            }
            key.add(String.valueOf(v));
        }
        return key;
    }

    private static CorrelationKey correlationKey(List<String> parts) {
        return KieInternalServices.Factory.get().newCorrelationKeyFactory().newCorrelationKey(parts);
    }

    private ProcessInstanceDesc bul(String ref) {
        if (ref.startsWith("pid-")) {
            ProcessInstanceDesc pi = runtimeDataService.getProcessInstanceById(Long.parseLong(ref.substring(4)));
            if (pi == null) {
                throw new BulunamadiException("Surec bulunamadi: " + ref);
            }
            return pi;
        }
        CorrelationKey ck = correlationKey(Arrays.asList(ref.split(":")));
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

    /** Ana surec ve tum alt surecleri (blok / call activity) icin aktif gorevler. */
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
