// =====================================================================
// AuditProcessEventListener - endNodeName ALAN ARA SIRA NULL SORUNU (v3)
// KESIN KOK NEDEN - gercek prod kodu (ekran goruntuleriyle dogrulandi)
// com.garantibbva.processarchitecture.springboot.listener.audit
// =====================================================================
//
// DOGRULANAN AKIS:
//
// 1) WRITE SIDE (saglam, sorun yok):
//    beforeNodeLeft(ProcessNodeLeftEvent event) HER node-left olayinda
//    kosulsuz olarak setProcessInstanceResult(event) cagiriyor. Bu metod
//    kendi icinde "instanceof EndNodeInstance" kontrolu yapiyor, yani
//    sadece process GERCEKTEN bir EndNode'a ulastiginda
//    processInstanceResultHistoryMap'e yaziyor. jBPM'in senkron execution
//    modeli geregi bu yazma, ayni process instance'in afterProcessCompleted
//    cagrisindan ONCE, ayni call stack icinde garanti olarak gerceklesir.
//    -> Bu taraf DUZGUN CALISIYOR, bug burada degil.
//
// 2) READ SIDE - KOK NEDEN BURADA:
//    afterProcessCompleted(ProcessCompletedEvent event):
//
//        prepareProcessDataAndSendToKafka(event, methodName,
//                MetadataConstants.COMPLETED, MetadataConstants.COMPLETED_DESC);
//
//    Bu cagri, event'in GERCEK process state'ine (COMPLETED mi ABORTED mi)
//    HIC BAKMADAN her zaman MetadataConstants.COMPLETED gonderiyor.
//
//    OYSA jBPM'de ProcessCompletedEvent hem process NORMAL bittiginde
//    (ProcessInstance.STATE_COMPLETED) HEM DE process abort/iptal/hata ile
//    sonlandiginda (ProcessInstance.STATE_ABORTED) ayni event tipiyle
//    tetiklenir.
//
//    getPayload(...) icindeki COMPLETED case'i:
//
//        case MetadataConstants.COMPLETED:
//            ...
//            if (processMetadataType.equals(MetadataConstants.COMPLETED)) {
//                payload.put(PayloadConstants.TERMINATOR, calculateTerminator(wi));
//                String endNodeName = getProcessInstanceResult(event);
//                payload.put(PayloadConstants.END_NODE_NAME, StringUtils.defaultString(endNodeName));
//            }
//            break;
//
//    getProcessInstanceResult(event), processInstanceResultHistoryMap'te
//    instanceId icin kayit yoksa null doner. Kayit SADECE process gercekten
//    bir EndNodeInstance'a ugradiysa yazilmis olur (bkz. madde 1). Process
//    ABORT ile bittiyse (timeout, exception, manuel iptal, vs.) hicbir zaman
//    bir EndNodeInstance'a ugramaz -> map'te kayit yoktur -> null.
//
//    Kod bunu zaten ongormus ve getProcessInstanceResult icinde
//    "Process instance result not found" seklinde warn logluyor - ama
//    null'u yine de Kafka payload'ina END_NODE_NAME olarak yaziyor.
//
//    SEMPTOMLE ORTUSME: Yuk arttikca (DB/servis timeout'lari, kaynak
//    tukenmesi, thread pool doluluk) ABORT ile sonlanan process sayisi da
//    artar. Bu process'ler yine de "COMPLETED" gibi islenip bir end node
//    adi aranir - ama hicbir zaman yazilmamistir. Bu da tam olarak
//    "bazen, islem sayisi cok oldugunda" davranisini acikliyor.
//
// ---------------------------------------------------------------------
// FIX: endNodeName'i sadece process GERCEKTEN normal bittiyse
// (STATE_COMPLETED) ara. ABORT durumunda hem gereksiz WARN log basmayi
// hem de yanlislikla null yazmayi engelle - STATUS alani zaten
// getStateAndDate(...) uzerinden ABORTED/ERROR olarak dogru donuyor,
// endNodeName alaninin ise bu durumda hic anlami yok.
// ---------------------------------------------------------------------

case MetadataConstants.AUTOMATIC_TASK_COMPLETED:
case MetadataConstants.COMPLETED:
    var stateAndDate = getStateAndDate(event, wi, processMetadataType);
    payload.put(PayloadConstants.STATUS, stateAndDate[0]);
    payload.put(PayloadConstants.END_DATE, stateAndDate[1]);

    if (processMetadataType.equals(MetadataConstants.COMPLETED)) {
        payload.put(PayloadConstants.TERMINATOR, calculateTerminator(wi));

        // FIX: sadece process gercekten bir EndNode'a ulasarak normal
        // bittiyse endNodeName ara. Aksi halde (ABORTED, vb.) map'te zaten
        // kayit olamayacagi icin aramaya bile girmeye gerek yok - ne
        // yanlis "not found" warn'i basilir ne de anlamsiz null yazilir.
        String endNodeName = null;
        if (Objects.nonNull(wi) && wi.getState() == ProcessInstance.STATE_COMPLETED) {
            endNodeName = getProcessInstanceResult(event);
        } else {
            log.debug("Process instance did not reach a normal end node "
                            + "(state={}), skipping endNodeName lookup :: instanceId: {}",
                    Objects.nonNull(wi) ? wi.getState() : "unknown",
                    resolveInstanceId(event));
        }
        payload.put(PayloadConstants.END_NODE_NAME, StringUtils.defaultString(endNodeName));
    }
    break;

// ---------------------------------------------------------------------
// EK ONERI (defense-in-depth, ayri bir olasi katki - hala gecerli):
// getProcessInstanceResult su an Map.remove() ile "consume-once" okuyor.
// sendToKafka(...) tarafinda (goremedigimiz kisim) retry/exception-sonrasi
// tekrar deneme varsa, ayni event icin getPayload ikinci kez cagrilirsa
// (ör. Kafka broker timeout + retry, ve retry TUM payload'i yeniden
// olusturuyorsa) ilk denemede map'ten silinen kayit ikinci denemede
// bulunamaz ve endNodeName yine null gider. sendToKafka'nin retry mantigini
// da kontrol etmenizi oneririm - varsa, once tum payload derlensin (ve
// map'ten silinsin), Kafka'ya gonderim retry'i bu HAZIR payload uzerinden
// yapilsin; payload'i her retry'da yeniden olusturmayin.
// ---------------------------------------------------------------------
