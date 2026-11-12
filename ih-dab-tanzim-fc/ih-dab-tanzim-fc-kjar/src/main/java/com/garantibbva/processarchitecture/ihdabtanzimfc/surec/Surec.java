package com.garantibbva.processarchitecture.ihdabtanzimfc.surec;

import com.garantibbva.processarchitecture.ihdabtanzimfc.model.ExportAll;
import org.kie.api.runtime.process.ProcessContext;

/**
 * BPMN script/kosul ifadelerinin kullandigi yardimcilar. Kosullari Java'da tek bir
 * yerde toplamak, BPMN icindeki ifadeleri kisa ve null-guvenli tutar.
 *
 * <p>Insan gorevleri ciktilarini gecici surec degiskenlerine yazar
 * ({@link #V_ACTION}, {@link #V_REWORK}, {@link #V_KUYRUK}); gorev cikisinda
 * {@link #gorevUygula} bunlari {@code exportAll} konteynerine kopyalar.
 */
public final class Surec {

    public static final String V_ACTION = "uAction";
    public static final String V_REWORK = "uRework";
    public static final String V_KUYRUK = "uKuyrukAdi";
    public static final String V_RETURN_CODE = "uReturnCode";
    public static final String V_HATA_KARAR = "uHataKarar";

    public static final String ONAY = "ONAY";
    public static final String IADE = "IADE";
    public static final String IPTAL = "IPTAL";
    public static final String ERTESI_GUN = "ERTESI_GUN";
    public static final String UYUM = "UYUM";
    public static final String ATLA = "ATLA";

    private Surec() {
    }

    public static boolean esit(String a, String b) {
        return a != null && a.equals(b);
    }

    public static boolean bos(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Avrasya ValueType="C" eslemesi: kaynak dugumlerin birlestirilmesi (null'lar atlanir). */
    public static String birlestir(String... parcalar) {
        StringBuilder sb = new StringBuilder();
        for (String p : parcalar) {
            if (p != null) sb.append(p);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------- servis

    /**
     * Servis cagrisi oncesi donus kodlarini temizler; boylece servis cevap
     * veremezse onceki adimdan kalan "00" yanlislikla basari sayilmaz.
     */
    public static void servisOncesi(ExportAll e) {
        e.ensureInitialized();
        e.getWip().setReturnCode(null);
        e.getWip().setStatusCode(null);
    }

    /** Referans Process Aciklama Ekle EndCondition: sWIP/status_code = "0". */
    public static boolean statusCodeSifir(ExportAll e, String serviceError) {
        return serviceError == null && e != null && e.getWip() != null && esit(e.getWip().getStatusCode(), "0");
    }

    /** Diger servislerin EndCondition'i: sWIP/return_code = "00". */
    public static boolean returnCodeTamam(ExportAll e, String serviceError) {
        return serviceError == null && e != null && e.getWip() != null && esit(e.getWip().getReturnCode(), "00");
    }

    // ------------------------------------------------------------- insan gorevi

    public static void gorevHazirla(ProcessContext ctx) {
        ctx.setVariable(V_ACTION, null);
        ctx.setVariable(V_REWORK, null);
        ctx.setVariable(V_KUYRUK, null);
        ctx.setVariable(V_RETURN_CODE, null);
        ctx.setVariable(V_HATA_KARAR, null);
    }

    /**
     * Gorev ciktilarini konteynere yazar (yalnizca gonderilen alanlar).
     * {@code returnCode} gonderilmezse "00" kabul edilir: tamamlanan bir bekleme
     * gorevi (Avrasya ProgramID 50911) basarili sayilir; tamamlayan sistem/kullanici
     * farkli bir kod gonderirse bitis kosulu saglanmaz ve gorev tekrar acilir.
     */
    public static void gorevUygula(ExportAll e, ProcessContext ctx) {
        e.ensureInitialized();
        String action = (String) ctx.getVariable(V_ACTION);
        String rework = (String) ctx.getVariable(V_REWORK);
        String kuyruk = (String) ctx.getVariable(V_KUYRUK);
        String returnCode = (String) ctx.getVariable(V_RETURN_CODE);
        e.getNav().setAction(bos(action) ? null : action.trim().toUpperCase());
        if (rework != null) e.getNav().setRework(rework.trim().toUpperCase());
        if (!bos(kuyruk)) e.getQue().setQKuyrukAdi(kuyruk.trim());
        e.getWip().setReturnCode(bos(returnCode) ? "00" : returnCode.trim());
        ctx.setVariable("exportAll", e);
    }

    public static boolean karar(ExportAll e, String deger) {
        return e != null && e.getNav() != null && esit(e.getNav().getAction(), deger);
    }

    /** Kullanici surecten cikis (IADE / IPTAL) istedi mi? */
    public static boolean cikisIstendi(ExportAll e) {
        return karar(e, IADE) || karar(e, IPTAL);
    }

    /** IADE/IPTAL kararini ENCLAVE yonlendirmesi icin sNAV/restart_req'e tasir. */
    public static void cikisIsaretle(ExportAll e, ProcessContext ctx) {
        e.ensureInitialized();
        e.getNav().setRestartReq(e.getNav().getAction());
        e.getNav().setAction(null);
        ctx.setVariable("exportAll", e);
    }

    /** DAB Tanzim / Yetkili EndCondition: sNAV/rework = "N" degilse gorev tekrar acilir (DLC). */
    public static boolean reworkTamam(ExportAll e) {
        return e != null && e.getNav() != null && esit(e.getNav().getRework(), "N");
    }

    public static boolean restartReq(ExportAll e, String deger) {
        return e != null && e.getNav() != null && esit(e.getNav().getRestartReq(), deger);
    }

    public static boolean hataAtla(ProcessContext ctx) {
        return esit((String) ctx.getVariable(V_HATA_KARAR), ATLA);
    }

    public static boolean hataAtla(String karar) {
        return esit(karar == null ? null : karar.trim().toUpperCase(), ATLA);
    }
}
