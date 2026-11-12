package com.garantibbva.processarchitecture.ihdabtanzimfc.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Avrasya surec konteyneri {@code sExport_sALL} (InputContainerID/OutputContainerID = 116075).
 * Tum aktivitelerin DDC eslemesi bu konteynerin tamamini tasir; BPMN'de tek bir
 * {@code exportAll} surec degiskeni olarak tutulur.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public class ExportAll implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("sExport_sWID")
    private ExportWid wid = new ExportWid();

    @JsonProperty("sExport_sQUE")
    private ExportQue que = new ExportQue();

    @JsonProperty("sExport_sWIP")
    private ExportWip wip = new ExportWip();

    @JsonProperty("sExport_sNAV")
    private ExportNav nav = new ExportNav();

    @JsonProperty("sExport_sPRO")
    private ExportPro pro = new ExportPro();

    public ExportAll() {
    }

    /** Alt yapilarin hicbiri null kalmasin (REST'ten eksik JSON gelebilir). */
    public ExportAll ensureInitialized() {
        if (wid == null) wid = new ExportWid();
        if (que == null) que = new ExportQue();
        if (wip == null) wip = new ExportWip();
        if (nav == null) nav = new ExportNav();
        if (pro == null) pro = new ExportPro();
        return this;
    }

    /**
     * Avrasya ProcessInfo/Attributes (RefBranchNum|RefCustomerNum|RefTrxType|RefSequenceNum)
     * karsiligi is anahtari. Surec, bu deger ile KIE correlation key olarak baslatilir.
     */
    public String businessKey() {
        ensureInitialized();
        return nz(wid.getJobReqUnitNum()) + "|" + nz(wid.getJobReqCustNum()) + "|"
                + nz(wid.getJobReqDocCode()) + "|" + nz(wid.getJobReqSeqNum());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public ExportWid getWid() { return wid; }
    public void setWid(ExportWid wid) { this.wid = wid; }
    public ExportQue getQue() { return que; }
    public void setQue(ExportQue que) { this.que = que; }
    public ExportWip getWip() { return wip; }
    public void setWip(ExportWip wip) { this.wip = wip; }
    public ExportNav getNav() { return nav; }
    public void setNav(ExportNav nav) { this.nav = nav; }
    public ExportPro getPro() { return pro; }
    public void setPro(ExportPro pro) { this.pro = pro; }

    @Override
    public String toString() {
        return "ExportAll{wid=" + wid + ", que=" + que + ", wip=" + wip + ", nav=" + nav + ", pro=" + pro + "}";
    }
}
