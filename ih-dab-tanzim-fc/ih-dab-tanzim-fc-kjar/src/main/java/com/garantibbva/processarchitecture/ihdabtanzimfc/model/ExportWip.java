package com.garantibbva.processarchitecture.ihdabtanzimfc.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** sExport_sWIP - servis donus kodlari (EndCondition). JSON alan adlari Avrasya konteyner dugum adlariyla birebir aynidir. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public class ExportWip implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Referans Process Aciklama Ekle basari: "0" */
    @JsonProperty("status_code")
    private String statusCode;

    /** Diger servisler basari: "00" */
    @JsonProperty("return_code")
    private String returnCode;

    public ExportWip() {
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(String returnCode) {
        this.returnCode = returnCode;
    }

    @Override
    public String toString() {
        return "ExportWip{" + "statusCode=" + statusCode + ", returnCode=" + returnCode + "}";
    }
}
