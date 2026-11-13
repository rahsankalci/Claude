package com.garantibbva.processarchitecture.ebrbasictest.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Avrasya surec konteyneri {@code sGK_sALL} (ContainerID 52249).
 *
 * <p>Surec tasariminda yalnizca {@code sGK_sWIP/status_code} referans ediliyor; konteynerin
 * geri kalanini mainframe programlari (54866, 269812) doldurur. Bu yuzden {@code sGK_sWIP}
 * tipli tutulur, diger tum dugumler ({@code sGK_sWID}, ...) {@link #getDiger()} icinde
 * AYNEN korunur ve servise/servisten oldugu gibi tasinir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public class GkAll implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("sGK_sWIP")
    private GkWip wip = new GkWip();

    /** Tipli olmayan diger konteyner dugumleri (mainframe'in doldurdugu alanlar). */
    private Map<String, Object> diger = new LinkedHashMap<>();

    public GkAll() {
    }

    public GkAll ensureInitialized() {
        if (wip == null) wip = new GkWip();
        if (diger == null) diger = new LinkedHashMap<>();
        return this;
    }

    public GkWip getWip() { return wip; }
    public void setWip(GkWip wip) { this.wip = wip; }

    @JsonAnyGetter
    public Map<String, Object> getDiger() { return diger; }

    @JsonAnySetter
    public void setDigerAlan(String ad, Object deger) { diger.put(ad, deger); }

    @Override
    public String toString() {
        return "GkAll{wip=" + wip + ", diger=" + diger + "}";
    }
}
