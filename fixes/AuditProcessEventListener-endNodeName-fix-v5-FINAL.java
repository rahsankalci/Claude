// =====================================================================
// AuditProcessEventListener - endNodeName NULL SORUNU - KOK NEDEN v5
// (v1-v4 iptal: pooling yok, tek pod icinde de degil - COK POD/OPENSHIFT)
// com.garantibbva.processarchitecture.springboot.listener.audit
// =====================================================================
//
// KESIN TESHIS:
//   - Uygulama Spring Boot olarak OpenShift'te calisiyor -> birden fazla
//     POD (replica) ile calisiyor. Session pooling YOK (dogrulandi).
//   - KIE'nin kendi tablolari (DB'ye persist edilir, TUM POD'LAR
//     ARASINDA PAYLASILAN ortak bir kaynaktir) endNodeName'i HER ZAMAN
//     dogru gosteriyor -> node traversal'in kendisi hep dogru calisiyor,
//     sorun orada degil.
//   - processInstanceResultHistoryMap ise AuditProcessEventListener
//     singleton bean'inin uzerinde JVM-HEAP'TE tutulan bir
//     ConcurrentHashMap. Bu map POD-LOCAL'dir: her pod'un kendi ayri
//     JVM'i, kendi ayri, BIRBIRINDEN HABERSIZ map'i vardir. Kubernetes/
//     OpenShift'te pod'lar arasinda paylasilan heap/bellek YOKTUR.
//
//   Eger bir process instance icin:
//     - beforeNodeLeft (EndNodeInstance'a ulasma, map'e YAZMA) POD A'da
//     - afterProcessCompleted (map'ten OKUMA) POD B'de
//   islenirse (mesaj kuyrugu uzerinden tetikleme, load balancing, ya da
//   her etkilesim icin session'in DB'den taze yuklenip pooling'siz/
//   disposable calistigi bir mimaride pod'lar arasi dagilim), POD B'nin
//   KENDI local map'inde o instance icin HICBIR ZAMAN kayit olmaz ->
//   NULL. KIE'nin DB'ye yazdigi veri ise pod'dan bagimsiz oldugu icin
//   HER ZAMAN dogrudur - tam da gozlemlediginiz durum.
//
//   Yuk arttikca (HPA scale-up ile pod sayisi artmasi + instance'larin
//   pod'lar arasina daha genis dagilmasi) bu cross-pod uyumsuzlugu
//   ihtimali de artiyor -> "islem sayisi cok oldugunda bazen null"
//   ile birebir ortusuyor.
//
// ---------------------------------------------------------------------
// FIX: JVM-local map'i TAMAMEN KALDIR. endNodeName'i KIE'nin zaten
// pod'lar arasi paylasilan/persist ettigi yere yaz: PROCESS INSTANCE
// VARIABLE olarak. beforeNodeLeft'te set edilen degisken,
// afterProcessCompleted hangi pod'da calisirsa calissin
// event.getProcessInstance() uzerinden doğrudan okunur - cunku bu artik
// JVM heap'i degil, KIE'nin process instance state'inin bir parcasi.
//
// processInstanceResultHistoryMap, ConcurrentHashMap importu,
// resolveInstanceId'in bu amacla kullanimi vs. TAMAMEN KALDIRILDI -
// gerek kalmadi.
// ---------------------------------------------------------------------

private static final String AUDIT_END_NODE_NAME_VAR = "___auditEndNodeName";


// ---------------------------------------------------------------------
// END NODE'DAN CIKISTA SONUCU YAZ
// Artik map'e degil, process instance'in kendi degiskenine yaziyor.
// Bu deger KIE'nin process instance state'inin bir parcasi oldugu icin
// hangi pod set ettiyse, hangi pod okursa okusun tutarlidir.
// ---------------------------------------------------------------------

private void setProcessInstanceResult(ProcessNodeLeftEvent event) {
    if (!(event.getNodeInstance() instanceof EndNodeInstance)) {
        return;
    }

    String endNodeName = StringUtils.defaultString(
            ((EndNodeInstance) event.getNodeInstance()).getEndNode().getName());

    log.debug("Process instance result stored :: instanceId: {}, endNodeName: {}",
            event.getProcessInstance().getId(), endNodeName);

    ((WorkflowProcessInstance) event.getProcessInstance())
            .setVariable(AUDIT_END_NODE_NAME_VAR, endNodeName);
}


// ---------------------------------------------------------------------
// SUREC TAMAMLANINCA SONUCU OKU
// Map lookup yok, pod-locality sorunu yok. event.getProcessInstance()
// zaten dogru (bu cagriyi alan pod'un DB'den yukledigi/elindeki) instance
// -> degiskeni oradan okumak KIE'nin kendi tablolariyla ayni kaynaktan
// (process instance state) okumak demek.
// ---------------------------------------------------------------------

private String getProcessInstanceResult(ProcessEvent event) {
    Object value = ((WorkflowProcessInstance) event.getProcessInstance())
            .getVariable(AUDIT_END_NODE_NAME_VAR);
    String result = value == null ? null : value.toString();

    if (result == null) {
        log.warn("Process instance result not found :: instanceId: {}, processId: {}",
                event.getProcessInstance().getId(), event.getProcessInstance().getProcessId());
    } else {
        log.debug("Process instance result :: instanceId: {}, result: {}",
                event.getProcessInstance().getId(), result);
    }

    return result;
}


// ---------------------------------------------------------------------
// KALDIRILANLAR (artik gerek yok):
//   - private final Map<String, String> processInstanceResultHistoryMap
//   - resolveInstanceId(...) [bu iki metod icin; baska yerde
//     kullaniliyorsa, ör. stateForAutomaticTasksMap icin, onu SILMEYIN -
//     bu fix SADECE endNodeName/processInstanceResultHistoryMap'i
//     ilgilendiriyor, stateForAutomaticTasksMap ayri bir mekanizma ve
//     bu fix'in kapsami disinda]
//   - java.util.concurrent.ConcurrentHashMap importu (baska yerde
//     kullanilmiyorsa)
//
// NOT: stateForAutomaticTasksMap (AutomaticTaskStorage icin kullanilan
// ayri map) bu analizin kapsami disinda tutuldu - ama AYNI pod-local
// heap sorununa o da acik olabilir. Otomatik gorevlerde de benzer
// "bazen state bulunamiyor" sikayeti aliyorsaniz (NullPointerException
// yakalanip loglanan senaryo, getStateAndDate icinde), kok neden ayni:
// o da JVM-local bir ConcurrentHashMap. Ayni pattern'le (process
// variable'a tasima) duzeltilebilir.
// ---------------------------------------------------------------------
