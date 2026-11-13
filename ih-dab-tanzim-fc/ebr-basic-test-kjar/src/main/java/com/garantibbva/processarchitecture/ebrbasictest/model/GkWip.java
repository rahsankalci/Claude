package com.garantibbva.processarchitecture.ebrbasictest.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code sGK_sALL/sGK_sWIP} - servis donus kodlari. Bilinmeyen alanlar korunur. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public class GkWip implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Referans Process Aciklama Ekle EndCondition: "0" */
    @JsonProperty("status_code")
    private String statusCode;

    @JsonProperty("return_code")
    private String returnCode;

    private Map<String, Object> diger = new LinkedHashMap<>();

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getReturnCode() { return returnCode; }
    public void setReturnCode(String returnCode) { this.returnCode = returnCode; }

    @JsonAnyGetter
    public Map<String, Object> getDiger() { return diger; }

    @JsonAnySetter
    public void setDigerAlan(String ad, Object deger) { diger.put(ad, deger); }

    @Override
    public String toString() {
        return "GkWip{statusCode=" + statusCode + ", returnCode=" + returnCode + ", diger=" + diger + "}";
    }
}
