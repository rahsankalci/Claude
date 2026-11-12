package com.garantibbva.processarchitecture.ihdabtanzimfc.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** sExport_sPRO - islem / onay bilgileri. JSON alan adlari Avrasya konteyner dugum adlariyla birebir aynidir. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public class ExportPro implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("status_code")
    private String statusCode;

    /** G = giris, 2 = yetkili 2. onay */
    @JsonProperty("approval_code")
    private String approvalCode;

    @JsonProperty("disable_flag")
    private String disableFlag;

    /** Onay tablosu tipi: O1 / O2 */
    @JsonProperty("HIST_APPROVAL")
    private String histApproval;

    /** WC Control cagiran uygulama: PESINMAL */
    @JsonProperty("caller_application")
    private String callerApplication;

    @JsonProperty("approval_flag")
    private String approvalFlag;

    public ExportPro() {
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getApprovalCode() {
        return approvalCode;
    }

    public void setApprovalCode(String approvalCode) {
        this.approvalCode = approvalCode;
    }

    public String getDisableFlag() {
        return disableFlag;
    }

    public void setDisableFlag(String disableFlag) {
        this.disableFlag = disableFlag;
    }

    public String getHistApproval() {
        return histApproval;
    }

    public void setHistApproval(String histApproval) {
        this.histApproval = histApproval;
    }

    public String getCallerApplication() {
        return callerApplication;
    }

    public void setCallerApplication(String callerApplication) {
        this.callerApplication = callerApplication;
    }

    public String getApprovalFlag() {
        return approvalFlag;
    }

    public void setApprovalFlag(String approvalFlag) {
        this.approvalFlag = approvalFlag;
    }

    @Override
    public String toString() {
        return "ExportPro{" + "statusCode=" + statusCode + ", approvalCode=" + approvalCode + ", disableFlag=" + disableFlag + ", histApproval=" + histApproval + ", callerApplication=" + callerApplication + ", approvalFlag=" + approvalFlag + "}";
    }
}
