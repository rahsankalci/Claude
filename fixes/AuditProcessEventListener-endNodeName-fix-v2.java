// =====================================================================
// AuditProcessEventListener - endNodeName ALAN ARA SIRA NULL SORUNU
// (v2 - onceki "instanceId key" duzeltmesinin uzerine ek fix)
// com.garantibbva.processarchitecture.springboot.listener.audit
// =====================================================================
//
// ONCEKI DUZELTME (v1) NEYI COZMUSTU?
//   processInstanceResultHistoryMap key'i process DEFINITION id yerine
//   process INSTANCE id yapilmisti. Bu, ayni process tanimindan ayni anda
//   birden fazla instance calisirken birbirinin sonucunu ezmesini
//   (cross-instance overwrite) engelliyordu. Dogru bir duzeltmeydi ama
//   asagidaki ikinci bug'i kapsamiyordu.
//
// KALAN BUG (v2 ile duzeltiliyor):
//   getProcessInstanceResult() metodu "consume-once" calisiyordu:
//   okuma sirasinda ayni anda map'ten SILIYORDU (Map.remove ile okuma).
//   Eger afterProcessCompleted() icindeki Kafka gonderim / audit hazirlama
//   akisinda BIR RETRY / exception-sonrasi-tekrar-deneme varsa (Kafka
//   backpressure, network timeout, vs.), 2. deneme ayni event icin bu
//   metodu tekrar cagirir. Ilk deneme kaydi zaten map'ten sildigi icin
//   2. deneme NULL doner.
//
//   Bu, "bazen ve islem sayisi cok oldugunda" semptomuyla birebir
//   ortusuyor: dusuk trafikte retry nadirdir, yuksek trafikte (broker
//   geri basinci, thread pool doluluk, timeout artisi) retry sikligi
//   artar.
//
//   IKINCI OLASI SEBEP (ayri bir durum, "bug" degil ama ayni semptomu
//   verir): process, normal bir EndNodeInstance'a hic ugramadan HATA/
//   ABORT ile bitiyorsa (STATE_ABORTED), setProcessInstanceResult hicbir
//   zaman cagrilmamis olur (instanceof EndNodeInstance kontrolu false
//   doner) ve afterProcessCompleted yine de tetiklenir -> map'te kayit
//   yoktur -> null DOGRUDUR, cunku o instance'in gercek bir "end node adi"
//   yoktur. Yuk altinda timeout/hata ile biten process sayisi arttigi
//   icin bu da "yuk altinda artan null" izlenimi verir.
//   Hangi senaryonun / ikisinin de gerceklestigini ayirt etmek icin
//   asagida STATE_ABORTED icin ayri bir log satiri eklendi.
//
// ---------------------------------------------------------------------

private final Map<String, String> processInstanceResultHistoryMap = new ConcurrentHashMap<>();


// ---------------------------------------------------------------------
// END NODE'DAN CIKISTA SONUCU YAZ (degisiklik yok, v1 ile ayni)
// ---------------------------------------------------------------------

private void setProcessInstanceResult(ProcessNodeLeftEvent event) {
    if (!(event.getNodeInstance() instanceof EndNodeInstance)) {
        return;
    }

    String instanceId = resolveInstanceId(event);
    String endNodeName = StringUtils.defaultString(
            ((EndNodeInstance) event.getNodeInstance()).getEndNode().getName());

    log.debug("Process instance result stored :: instanceId: {}, endNodeName: {}",
            instanceId, endNodeName);

    processInstanceResultHistoryMap.put(instanceId, endNodeName);
}


// ---------------------------------------------------------------------
// FIX: NON-DESTRUCTIVE OKUMA
// Onceki hali map.remove() ile okuyup siliyordu (consume-once).
// Retry / ikinci cagri durumunda bu NULL'e sebep oluyordu.
// Artik sadece get() yapiyor; silme islemi TEK bir yerde,
// afterProcessCompleted'in finally blogunda garanti ediliyor.
// ---------------------------------------------------------------------

private String peekProcessInstanceResult(ProcessEvent event) {
    String instanceId = resolveInstanceId(event);
    String result = processInstanceResultHistoryMap.get(instanceId);

    if (result == null) {
        boolean aborted = event.getProcessInstance().getState()
                == org.kie.api.runtime.process.ProcessInstance.STATE_ABORTED;

        if (aborted) {
            // Bu instance normal bir EndNodeInstance'a hic ugramadan
            // hata/abort ile bitmis olabilir - bu durumda null BEKLENEN
            // bir sonuctur, bug degildir. Prod'da hangi senaryonun
            // gerceklestigini olcmek icin bu log'u izleyin.
            log.warn("Process instance result not found (instance ABORTED, "
                            + "muhtemelen EndNodeInstance'a hic ulasmadi) :: "
                            + "instanceId: {}, processId: {}",
                    instanceId, event.getProcessInstance().getProcessId());
        } else {
            // Instance normal COMPLETED durumunda ama kayit yok.
            // Bu asil arastirilmasi gereken durum: retry / duplicate
            // event / event-siralama sorunu ihtimali burada.
            log.warn("Process instance result not found (instance COMPLETED "
                            + "ama map'te kayit yok - retry/duplicate cagri "
                            + "ihtimalini kontrol edin) :: instanceId: {}, processId: {}",
                    instanceId, event.getProcessInstance().getProcessId());
        }
    } else {
        log.debug("Process instance result :: instanceId: {}, result: {}",
                instanceId, result);
    }

    return result;
}


private String resolveInstanceId(ProcessEvent event) {
    return String.valueOf(event.getProcessInstance().getId());
}


// ---------------------------------------------------------------------
// TEK SILME NOKTASI
// getProcessInstanceResult artik silmiyor (bkz. peekProcessInstanceResult).
// Silme islemi SADECE burada, finally icinde yapiliyor. Boylece:
//   - Kafka gonderimi retry edilse bile 2. denemede ayni deger okunur
//     (peek destructive olmadigi icin).
//   - Kafka gonderimi (retry dahil) kesin olarak bitince ya da exception
//     firlatinca map'ten kayit temizlenir, leak olmaz.
// ---------------------------------------------------------------------

@Override
public void afterProcessCompleted(ProcessCompletedEvent event) {
    try {

        // >>> MEVCUT AUDIT / KAFKA GONDERIM MANTIGIN BURAYA <<<
        // ornek:
        // String processResult = peekProcessInstanceResult(event);
        // prepareProcessDataAndSendToKafka(event, methodName, processName, processResult);
        //
        // ONEMLI: Kafka gonderiminde retry logic varsa (@Retryable, manuel
        // try/catch ile tekrar deneme, vs.) peekProcessInstanceResult'i
        // her denemede tekrar cagirmak artik GUVENLI - destructive degil.

    } finally {
        processInstanceResultHistoryMap.remove(resolveInstanceId(event));
    }
}


// ---------------------------------------------------------------------
// ONERI (opsiyonel, ekstra saglamlastirma):
// Eger surec cok uzun surebiliyor ya da bir sekilde afterProcessCompleted
// hicbir zaman tetiklenmeyen instance'lar olabiliyorsa (ornegin process
// suspend/timeout ile sonsuza kadar map'te kalabiliyorsa), map'e bir
// boyut/TTL sinirlamasi (ornegin Caffeine Cache maximumSize+expireAfterWrite)
// eklemek memory leak'e karsi ek guvenlik saglar. Mevcut trafik hacminizde
// bunun gerekip gerekmedigini map boyutunu periyodik loglayarak (ornegin
// bir scheduled health-check ile processInstanceResultHistoryMap.size())
// dogrulamanizi oneririm.
// ---------------------------------------------------------------------
