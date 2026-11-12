package com.garantibbva.processarchitecture.ihdabtanzimfc.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IH_DAB_TANZIM_FC calisma zamani: KIE Server (jBPM) + entegrasyon REST uclari.
 * <ul>
 *   <li>KIE Server REST: {@code /rest/server/...} (standart BAMOE/KIE Server API)</li>
 *   <li>Entegrasyon: {@code /api/ih-dab-tanzim-fc/...} (bkz. {@link SurecEntegrasyonController})</li>
 * </ul>
 */
@SpringBootApplication
public class IhDabTanzimFcApplication {

    public static void main(String[] args) {
        SpringApplication.run(IhDabTanzimFcApplication.class, args);
    }
}
