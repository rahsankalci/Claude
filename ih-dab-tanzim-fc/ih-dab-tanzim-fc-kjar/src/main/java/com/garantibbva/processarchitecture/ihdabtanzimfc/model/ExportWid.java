package com.garantibbva.processarchitecture.ihdabtanzimfc.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** sExport_sWID - is istegi kimligi (ProcessInfo/Attributes referanslari). JSON alan adlari Avrasya konteyner dugum adlariyla birebir aynidir. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public class ExportWid implements Serializable {

    private static final long serialVersionUID = 1L;

    /** RefBranchNum - sube no */
    @JsonProperty("job_req_unit_num")
    private String jobReqUnitNum;

    /** RefCustomerNum - musteri no */
    @JsonProperty("job_req_cust_num")
    private String jobReqCustNum;

    /** RefTrxType - islem tipi */
    @JsonProperty("job_req_doc_code")
    private String jobReqDocCode;

    /** RefSequenceNum - sira no */
    @JsonProperty("job_req_seq_num")
    private String jobReqSeqNum;

    public ExportWid() {
    }

    public String getJobReqUnitNum() {
        return jobReqUnitNum;
    }

    public void setJobReqUnitNum(String jobReqUnitNum) {
        this.jobReqUnitNum = jobReqUnitNum;
    }

    public String getJobReqCustNum() {
        return jobReqCustNum;
    }

    public void setJobReqCustNum(String jobReqCustNum) {
        this.jobReqCustNum = jobReqCustNum;
    }

    public String getJobReqDocCode() {
        return jobReqDocCode;
    }

    public void setJobReqDocCode(String jobReqDocCode) {
        this.jobReqDocCode = jobReqDocCode;
    }

    public String getJobReqSeqNum() {
        return jobReqSeqNum;
    }

    public void setJobReqSeqNum(String jobReqSeqNum) {
        this.jobReqSeqNum = jobReqSeqNum;
    }

    @Override
    public String toString() {
        return "ExportWid{" + "jobReqUnitNum=" + jobReqUnitNum + ", jobReqCustNum=" + jobReqCustNum + ", jobReqDocCode=" + jobReqDocCode + ", jobReqSeqNum=" + jobReqSeqNum + "}";
    }
}
