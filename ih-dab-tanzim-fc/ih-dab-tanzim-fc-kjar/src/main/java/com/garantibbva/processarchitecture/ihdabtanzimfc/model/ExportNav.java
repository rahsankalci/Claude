package com.garantibbva.processarchitecture.ihdabtanzimfc.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** sExport_sNAV - navigasyon / yonlendirme. JSON alan adlari Avrasya konteyner dugum adlariyla birebir aynidir. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public class ExportNav implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ENCLAVE yonlendirmesi: IPTAL | IADE | bos */
    @JsonProperty("restart_req")
    private String restartReq;

    /** Sube iade sonrasi temizlenir */
    @JsonProperty("restart_act")
    private String restartAct;

    /** Insan gorevi EndCondition: "N" olmadikca gorev tekrar acilir */
    @JsonProperty("rework")
    private String rework;

    /** [YENI] Insan gorevi karari (ONAY, IADE, IPTAL, ERTESI_GUN, UYUM, TEMIZ) - kesik XML'de olmayan baglanti kosullarinin yerine */
    @JsonProperty("action")
    private String action;

    public ExportNav() {
    }

    public String getRestartReq() {
        return restartReq;
    }

    public void setRestartReq(String restartReq) {
        this.restartReq = restartReq;
    }

    public String getRestartAct() {
        return restartAct;
    }

    public void setRestartAct(String restartAct) {
        this.restartAct = restartAct;
    }

    public String getRework() {
        return rework;
    }

    public void setRework(String rework) {
        this.rework = rework;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "ExportNav{" + "restartReq=" + restartReq + ", restartAct=" + restartAct + ", rework=" + rework + ", action=" + action + "}";
    }
}
