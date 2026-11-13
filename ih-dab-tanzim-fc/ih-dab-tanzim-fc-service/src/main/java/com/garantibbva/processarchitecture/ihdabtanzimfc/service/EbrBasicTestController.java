package com.garantibbva.processarchitecture.ihdabtanzimfc.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jbpm.services.api.DeploymentService;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.model.ProcessDefinition;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.internal.runtime.manager.InternalRuntimeManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EBR_BASIC_TEST uclari. Avrasya tasariminda referans attribute'lari (RefBranchNum vb.) bos
 * oldugu icin surec process instance id ile izlenir.
 * <pre>
 * POST /api/ebr-basic-test/surecler          govde: sGK_sALL JSON  -> {"processInstanceId":..}
 * GET  /api/ebr-basic-test/surecler/{pid}                          -> durum (+ konteyner, aktifse)
 * </pre>
 */
@RestController
@RequestMapping("/api/ebr-basic-test")
public class EbrBasicTestController {

    static final String PROCESS_ID = "EBR_BASIC_TEST";
    static final String MODEL_CLASS = "com.garantibbva.processarchitecture.ebrbasictest.model.GkAll";

    private final ProcessService processService;
    private final RuntimeDataService runtimeDataService;
    private final DeploymentService deploymentService;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public EbrBasicTestController(ProcessService processService, RuntimeDataService runtimeDataService,
                                  DeploymentService deploymentService) {
        this.processService = processService;
        this.runtimeDataService = runtimeDataService;
        this.deploymentService = deploymentService;
    }

    @PostMapping("/surecler")
    public ResponseEntity<Map<String, Object>> baslat(@RequestBody Map<String, Object> gkAll) throws Exception {
        String deploymentId = runtimeDataService.getProcessesById(PROCESS_ID).stream()
                .map(ProcessDefinition::getDeploymentId)
                .filter(deploymentService::isDeployed)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException(PROCESS_ID + " deploy edilmemis"));
        ClassLoader cl = ((InternalRuntimeManager) deploymentService.getDeployedUnit(deploymentId).getRuntimeManager())
                .getEnvironment().getClassLoader();
        Object model = mapper.convertValue(gkAll, Class.forName(MODEL_CLASS, true, cl));

        Map<String, Object> params = new HashMap<>();
        params.put("gkAll", model);
        Long pid = processService.startProcess(deploymentId, PROCESS_ID, params);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("processInstanceId", pid);
        res.put("deploymentId", deploymentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/surecler/{pid}")
    public ResponseEntity<Map<String, Object>> durum(@PathVariable long pid) {
        ProcessInstanceDesc pi = runtimeDataService.getProcessInstanceById(pid);
        if (pi == null || !PROCESS_ID.equals(pi.getProcessId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("hata", "Surec bulunamadi: " + pid));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("processInstanceId", pid);
        res.put("state", pi.getState() == ProcessInstance.STATE_ACTIVE ? "ACTIVE"
                : pi.getState() == ProcessInstance.STATE_COMPLETED ? "COMPLETED"
                : pi.getState() == ProcessInstance.STATE_ABORTED ? "ABORTED" : String.valueOf(pi.getState()));
        if (pi.getState() == ProcessInstance.STATE_ACTIVE) {
            Object gk = processService.getProcessInstanceVariables(pi.getDeploymentId(), pid).get("gkAll");
            res.put("sGK_sALL", gk == null ? null : mapper.convertValue(gk, Map.class));
            res.put("bekleyenServisler", processService.getWorkItemByProcessInstance(pi.getDeploymentId(), pid).stream()
                    .map(w -> w.getParameter("ActivityName")).toArray());
        }
        return ResponseEntity.ok(res);
    }
}
