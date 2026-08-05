// =====================================================================
// AuditProcessEventListener - endNodeName YUK ALTINDA NULL SORUNU (v4)
// KESIN FIX - kaynagi (retry / duplicate listener / redelivery) ne
// olursa olsun ayni event icin coklu cagriya dayanikli okuma
// com.garantibbva.processarchitecture.springboot.listener.audit
// =====================================================================
//
// GUNCEL TESHIS (kullanicidan gelen dogrulama ile):
//   - Kafka'ya yazma (sendToKafka) ortak/paylasilan kod, hicbir zaman
//     sorun cikarmiyor -> gonderim/retry orada degil.
//   - Null giden instance'larin surecleri DUZGUN COMPLETE olmus, kendi
//     audit tablosuna endNodeName dogru yazilmis -> write-side
//     (beforeNodeLeft -> setProcessInstanceResult) SAGLAM calisiyor.
//   - Sorun, EVENT MESAJI URETILIRKEN (getPayload/getProcessInstanceResult
//     cagrildigi an) ortaya cikiyor.
//   - Yuk cok oldugunda (orn. 4000 islemde ~200 tanesi) olusuyor.
//
// EN GUCLU ACIKLAMA: "pool" hipotezi
//   jBPM'de KieSession'lar bir pool'dan alinip kullanilip geri
//   birakiliyorsa ve her checkout'ta listener session'a
//   addEventListener() ile ekleniyor ama checkin'de karsilik gelen bir
//   removeEventListener() yapilmiyorsa (ya da idempotent degilse), ayni
//   session tekrar tekrar reuse edildikce AYNI listener session'a
//   BIRDEN FAZLA KEZ kayitli hale gelir. Bu durumda TEK bir gercek
//   afterProcessCompleted olayi, o session'da N kez tetiklenir:
//
//     1. cagri: map'te deger var -> dogru endNodeName okunur VE SILINIR
//                -> bu payload'da endNodeName doludur
//     2..N cagri: map bos -> null -> BU payload'larda endNodeName null'dur
//
//   Downstream'e giden N mesajdan biri null olabilir. Yuk arttikca
//   pool baskisi nedeniyle session reuse sikligi da arttigi icin bu
//   "coklu tetiklenme" ihtimali de artiyor -> "islem sayisi cok
//   oldugunda bazen null" ile birebir ortusuyor.
//
//   (MessagingKafkaConfiguration.java'daki session/listener wiring
//   incelenerek bu kesinlestirilecek - ayri olarak isteniyor.)
//
// KOKENI NE OLURSA OLSUN GECERLI FIX:
//   getProcessInstanceResult'i DESTRUCTIVE olmaktan cikar. Ayni event
//   icin kac kez cagrilirsa cagrilsin (retry / duplicate listener /
//   message redelivery - sebebi onemli degil) hep AYNI DOGRU degeri
//   dondursun. Manuel remove() yerine TTL (zaman asimi) ile otomatik
//   temizlenen bir yapi kullan - boylece "ne zaman guvenle silebilirim"
//   sorusunu cozmeye ugrasmaya gerek kalmiyor, hafiza sizintisi da
//   olmuyor.
// ---------------------------------------------------------------------

private static final long RESULT_TTL_MILLIS = java.util.concurrent.TimeUnit.MINUTES.toMillis(15);

private static final class TimestampedResult {
    private final String value;
    private final long storedAt;

    private TimestampedResult(String value) {
        this.value = value;
        this.storedAt = System.currentTimeMillis();
    }

    private boolean isExpired(long now) {
        return now - storedAt > RESULT_TTL_MILLIS;
    }
}

private final Map<String, TimestampedResult> processInstanceResultHistoryMap = new ConcurrentHashMap<>();


// ---------------------------------------------------------------------
// END NODE'DAN CIKISTA SONUCU YAZ
// (mantik degismedi, sadece deger TimestampedResult'a sarildi + fircayla
// eski kayitlari opportunistically temizliyoruz)
// ---------------------------------------------------------------------

private void setProcessInstanceResult(ProcessNodeLeftEvent event) {
    if (!(event.getNodeInstance() instanceof EndNodeInstance)) {
        return;
    }

    sweepExpiredResults();

    String instanceId = resolveInstanceId(event);
    String endNodeName = StringUtils.defaultString(
            ((EndNodeInstance) event.getNodeInstance()).getEndNode().getName());

    log.debug("Process instance result stored :: instanceId: {}, endNodeName: {}",
            instanceId, endNodeName);

    processInstanceResultHistoryMap.put(instanceId, new TimestampedResult(endNodeName));
}


// ---------------------------------------------------------------------
// FIX: NON-DESTRUCTIVE OKUMA
// Artik remove() YOK. Ayni event icin N kez cagrilsa da (pool kaynakli
// duplicate listener, retry, redelivery - sebebi ne olursa olsun) her
// zaman ayni dogru degeri dondurur. Kayit, TTL suresi dolana kadar
// map'te kalir, sonra sweepExpiredResults() ile temizlenir.
// ---------------------------------------------------------------------

private String getProcessInstanceResult(ProcessEvent event) {
    String instanceId = resolveInstanceId(event);
    TimestampedResult stored = processInstanceResultHistoryMap.get(instanceId);

    if (stored == null) {
        log.warn("Process instance result not found :: instanceId: {}, processId: {}",
                instanceId, event.getProcessInstance().getProcessId());
        return null;
    }

    log.debug("Process instance result :: instanceId: {}, result: {}",
            instanceId, stored.value);
    return stored.value;
}


private String resolveInstanceId(ProcessEvent event) {
    return String.valueOf(event.getProcessInstance().getId());
}


// ---------------------------------------------------------------------
// TTL SUPURME
// Dependency eklemeden (Caffeine vs. gerektirmeden), her yazma
// isleminde opportunistic olarak suresi gecmis kayitlari temizler.
// Map buyuklugu normal trafikte kucuk kalacagi icin bu islem ucuzdur.
// ---------------------------------------------------------------------

private void sweepExpiredResults() {
    long now = System.currentTimeMillis();
    processInstanceResultHistoryMap.values().removeIf(result -> result.isExpired(now));
}

// ---------------------------------------------------------------------
// NOT: Eger MessagingKafkaConfiguration.java'da gercekten
// "her checkout'ta addEventListener, checkin'de removeEventListener yok"
// paterni dogrulanirsa, BU FIX SEMPTOMU ORTADAN KALDIRIR ama kok nedeni
// (listener'in session'a N kez kayitli olmasi -> her event'in N kere
// islenmesi, N kat gereksiz Kafka mesaji/CPU/network yuku) COZMEZ.
// O durumda asil duzeltme session lifecycle'ina
// (checkout/checkin sirasinda listener'in tam olarak BIR KEZ kayitli
// olmasini garanti etmek) yapilmalidir. Bu dosya sadece semptomu
// (null endNodeName) her senaryoda onleyen savunma katmanidir.
// ---------------------------------------------------------------------
