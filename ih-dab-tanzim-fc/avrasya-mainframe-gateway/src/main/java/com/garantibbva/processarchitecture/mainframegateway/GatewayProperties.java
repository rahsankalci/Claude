package com.garantibbva.processarchitecture.mainframegateway;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <pre>
 * gateway.connector=simulator | zos-connect
 * gateway.programs.269812.mode=async
 * gateway.programs.269812.url=https://zosconnect:9443/ebr/webclient     (zos-connect)
 * gateway.bpm.username / gateway.bpm.password                          (geri donus kimligi; Secret'tan)
 * </pre>
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /** simulator (gelistirme) veya zos-connect. */
    private String connector = "simulator";
    private int timeoutMs = 60_000;
    private Map<String, Program> programs = new HashMap<>();
    private Bpm bpm = new Bpm();

    public static class Program {
        /** sync (varsayilan) | async */
        private String mode = "sync";
        /** Mainframe (z/OS Connect API) adresi. */
        private String url;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class Bpm {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public boolean isAsync(String programId) {
        Program p = programs.get(programId);
        return p != null && "async".equalsIgnoreCase(p.getMode());
    }

    public String getConnector() { return connector; }
    public void setConnector(String connector) { this.connector = connector; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public Map<String, Program> getPrograms() { return programs; }
    public void setPrograms(Map<String, Program> programs) { this.programs = programs; }
    public Bpm getBpm() { return bpm; }
    public void setBpm(Bpm bpm) { this.bpm = bpm; }
}
