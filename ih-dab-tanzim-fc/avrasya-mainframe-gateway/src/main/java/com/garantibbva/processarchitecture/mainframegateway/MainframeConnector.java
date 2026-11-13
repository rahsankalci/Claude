package com.garantibbva.processarchitecture.mainframegateway;

import java.util.Map;

/** Mainframe programini konteynerle calistirir ve guncel konteyneri dondurur. */
public interface MainframeConnector {

    /**
     * @param programId Avrasya ProgramID
     * @param data      surec konteyneri (sGK_sALL / sExport_sALL)
     * @return mainframe'in dondurdugu konteyner
     * @throws MainframeException program hata/abend dondu veya mainframe'e ulasilamadi
     */
    Map<String, Object> call(String programId, Map<String, Object> data);

    class MainframeException extends RuntimeException {
        public MainframeException(String m) {
            super(m);
        }

        public MainframeException(String m, Throwable t) {
            super(m, t);
        }
    }
}
