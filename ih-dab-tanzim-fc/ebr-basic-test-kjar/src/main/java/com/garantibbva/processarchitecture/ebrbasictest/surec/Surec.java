package com.garantibbva.processarchitecture.ebrbasictest.surec;

import com.garantibbva.processarchitecture.ebrbasictest.model.GkAll;

/** EBR_BASIC_TEST BPMN script/kosul yardimcilari. */
public final class Surec {

    public static final String ATLA = "ATLA";

    private Surec() {
    }

    /** Servis oncesi donus kodu temizlenir; servis yanit vermezse eski "0" basari sayilmaz. */
    public static void servisOncesi(GkAll e) {
        e.ensureInitialized();
        e.getWip().setStatusCode(null);
        e.getWip().setReturnCode(null);
    }

    /** Referans Process Aciklama Ekle EndCondition: sGK_sWIP/status_code = "0". */
    public static boolean statusCodeSifir(GkAll e, String serviceError) {
        return serviceError == null && e != null && e.getWip() != null && "0".equals(e.getWip().getStatusCode());
    }

    /** WebClient EndCondition TRUE: yalnizca teknik hata (mainframe'e ulasilamadi / hata dondu) durdurur. */
    public static boolean teknikHataYok(GkAll e, String serviceError) {
        return serviceError == null;
    }

    public static boolean hataAtla(String karar) {
        return karar != null && ATLA.equalsIgnoreCase(karar.trim());
    }
}
