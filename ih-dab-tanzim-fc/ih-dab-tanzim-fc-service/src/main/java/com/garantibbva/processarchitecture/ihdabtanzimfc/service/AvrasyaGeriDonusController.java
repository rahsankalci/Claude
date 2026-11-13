package com.garantibbva.processarchitecture.ihdabtanzimfc.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.garantibbva.processarchitecture.ihdabtanzimfc.handler.AvrasyaServiceWorkItemHandler;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mainframe -> BPM geri donus ucu.
 *
 * <p>Asenkron Avrasya programlari (orn. EBR_BASIC_TEST / WebClient, ProgramID 269812) mainframe'e
 * gider; {@link AvrasyaServiceWorkItemHandler} work item'i ACIK birakir ve istege
 * {@code callbackUrl = .../geri-donus/{processInstanceId}/{workItemId}} ekler. Mainframe (veya
 * aradaki gateway) isi bitirince buraya POST eder:
 * <pre>
 * {"data": { ...konteyner (sGK_sALL / sExport_sALL)... }}     -> basarili
 * {"serviceError": "CICS ABEND ..."}                          -> hata: is Hata Kuyruguna duser
 * </pre>
 * Work item tamamlanir ve surec kaldigi yerden devam eder. Durum DB'de oldugu icin geri donus
 * hangi pod'a gelirse gelsin calisir.
 */
@RestController
@RequestMapping("/api/avrasya/geri-donus")
public class AvrasyaGeriDonusController {

    private static final Logger log = LoggerFactory.getLogger(AvrasyaGeriDonusController.class);

    private final ProcessService processService;
    private final RuntimeDataService runtimeDataService;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final int denemeSayisi;
    private final long denemeAraligiMs;

    public AvrasyaGeriDonusController(ProcessService processService, RuntimeDataService runtimeDataService,
                                      @Value("${avrasya.geri-donus.deneme-sayisi:10}") int denemeSayisi,
                                      @Value("${avrasya.geri-donus.deneme-araligi-ms:300}") long denemeAraligiMs) {
        this.processService = processService;
        this.runtimeDataService = runtimeDataService;
        this.denemeSayisi = denemeSayisi;
        this.denemeAraligiMs = denemeAraligiMs;
    }

    @PostMapping("/{processInstanceId}/{workItemId}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> geriDonus(@PathVariable long processInstanceId,
                                                         @PathVariable long workItemId,
                                                         @RequestBody Map<String, Object> govde) throws InterruptedException {
        ProcessInstanceDesc pi = runtimeDataService.getProcessInstanceById(processInstanceId);
        if (pi == null) {
            return hata(HttpStatus.NOT_FOUND, "Surec bulunamadi: " + processInstanceId);
        }
        if (pi.getState() != ProcessInstance.STATE_ACTIVE) {
            return hata(HttpStatus.CONFLICT, "Surec aktif degil (state " + pi.getState() + ")");
        }
        WorkItem wi = bekleyenWorkItem(pi.getDeploymentId(), processInstanceId, workItemId);
        if (wi == null) {
            return hata(HttpStatus.NOT_FOUND, "Bekleyen work item yok (zaten tamamlanmis olabilir): " + workItemId);
        }
        if (!AvrasyaServiceWorkItemHandler.NAME.equals(wi.getName())) {
            return hata(HttpStatus.BAD_REQUEST, "Work item bir Avrasya servis cagrisi degil: " + wi.getName());
        }

        Object mevcut = wi.getParameter(AvrasyaServiceWorkItemHandler.P_DATA);
        Object yeni = mevcut;
        Object data = govde.containsKey("data") || govde.containsKey("serviceError") ? govde.get("data") : govde;
        if (data instanceof Map && mevcut != null) {
            // kjar'in kendi sinifina (GkAll / ExportAll) cevir
            yeni = mapper.convertValue(data, mevcut.getClass());
        }
        Object err = govde.get("serviceError");
        Map<String, Object> results = new HashMap<>();
        results.put(AvrasyaServiceWorkItemHandler.R_DATA, yeni);
        results.put(AvrasyaServiceWorkItemHandler.R_SERVICE_ERROR, err == null ? null : String.valueOf(err));
        processService.completeWorkItem(pi.getDeploymentId(), processInstanceId, workItemId, results);
        log.info("Mainframe geri donusu islendi :: pid={}, workItem={}, program={}, aktivite={}, hata={}",
                processInstanceId, workItemId, wi.getParameter(AvrasyaServiceWorkItemHandler.P_PROGRAM_ID),
                wi.getParameter(AvrasyaServiceWorkItemHandler.P_ACTIVITY_NAME), err);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("processInstanceId", processInstanceId);
        res.put("workItemId", workItemId);
        res.put("tamamlandi", true);
        return ResponseEntity.ok(res);
    }

    /**
     * Mainframe cok hizli donerse, work item'i olusturan transaction henuz commit edilmemis
     * olabilir; kisa bir sure tekrar denenir.
     */
    private WorkItem bekleyenWorkItem(String deploymentId, long pid, long wid) throws InterruptedException {
        for (int i = 0; i < denemeSayisi; i++) {
            try {
                WorkItem wi = processService.getWorkItem(deploymentId, pid, wid);
                if (wi != null) {
                    return wi;
                }
            } catch (RuntimeException notYet) {
                log.debug("Work item {} henuz gorunmuyor ({}. deneme): {}", wid, i + 1, notYet.getMessage());
            }
            Thread.sleep(denemeAraligiMs);
        }
        return null;
    }

    private static ResponseEntity<Map<String, Object>> hata(HttpStatus s, String m) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("hata", m);
        return ResponseEntity.status(s).body(b);
    }
}
