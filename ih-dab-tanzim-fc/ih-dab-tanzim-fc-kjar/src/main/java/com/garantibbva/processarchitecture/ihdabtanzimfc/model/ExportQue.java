package com.garantibbva.processarchitecture.ihdabtanzimfc.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** sExport_sQUE - kuyruk / portfoy bilgileri. JSON alan adlari Avrasya konteyner dugum adlariyla birebir aynidir. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public class ExportQue implements Serializable {

    private static final long serialVersionUID = 1L;

    /** RefPriorityCode - oncelik */
    @JsonProperty("qOncelikKodu")
    private String qOncelikKodu;

    /** Portfoy adi (kuyruk on eki) */
    @JsonProperty("qPortfoyAdi")
    private String qPortfoyAdi;

    /** Ileri valor kuyruk son eki (dolu ise TIMEOUT adimi calisir - varsayim) */
    @JsonProperty("qIleriValor")
    private String qIleriValor;

    /** Sube iade (DOKUMAN BEKLEME) kuyruk son eki */
    @JsonProperty("qYarim")
    private String qYarim;

    /** Yetkili 2. onay kuyruk son eki */
    @JsonProperty("qYetkiliOnay")
    private String qYetkiliOnay;

    /** Aktif is kuyrugu (WorkQueueFromContainer) */
    @JsonProperty("qKuyrukAdi")
    private String qKuyrukAdi;

    /** Fircosoft kuyrugu (WorkQueueFromContainer) */
    @JsonProperty("qdetermine_portfolio")
    private String qDeterminePortfolio;

    public ExportQue() {
    }

    public String getQOncelikKodu() {
        return qOncelikKodu;
    }

    public void setQOncelikKodu(String qOncelikKodu) {
        this.qOncelikKodu = qOncelikKodu;
    }

    public String getQPortfoyAdi() {
        return qPortfoyAdi;
    }

    public void setQPortfoyAdi(String qPortfoyAdi) {
        this.qPortfoyAdi = qPortfoyAdi;
    }

    public String getQIleriValor() {
        return qIleriValor;
    }

    public void setQIleriValor(String qIleriValor) {
        this.qIleriValor = qIleriValor;
    }

    public String getQYarim() {
        return qYarim;
    }

    public void setQYarim(String qYarim) {
        this.qYarim = qYarim;
    }

    public String getQYetkiliOnay() {
        return qYetkiliOnay;
    }

    public void setQYetkiliOnay(String qYetkiliOnay) {
        this.qYetkiliOnay = qYetkiliOnay;
    }

    public String getQKuyrukAdi() {
        return qKuyrukAdi;
    }

    public void setQKuyrukAdi(String qKuyrukAdi) {
        this.qKuyrukAdi = qKuyrukAdi;
    }

    public String getQDeterminePortfolio() {
        return qDeterminePortfolio;
    }

    public void setQDeterminePortfolio(String qDeterminePortfolio) {
        this.qDeterminePortfolio = qDeterminePortfolio;
    }

    @Override
    public String toString() {
        return "ExportQue{" + "qOncelikKodu=" + qOncelikKodu + ", qPortfoyAdi=" + qPortfoyAdi + ", qIleriValor=" + qIleriValor + ", qYarim=" + qYarim + ", qYetkiliOnay=" + qYetkiliOnay + ", qKuyrukAdi=" + qKuyrukAdi + ", qDeterminePortfolio=" + qDeterminePortfolio + "}";
    }
}
