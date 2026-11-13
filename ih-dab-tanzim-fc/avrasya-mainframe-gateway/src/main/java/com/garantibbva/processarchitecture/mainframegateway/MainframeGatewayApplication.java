package com.garantibbva.processarchitecture.mainframegateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Avrasya Mainframe Gateway.
 *
 * <pre>
 * BPM (AvrasyaServiceWorkItemHandler) --POST /avrasya/{programId}--> Gateway --> Mainframe (z/OS Connect / CICS)
 *   senkron program : sonuc ayni HTTP yanitinda doner (200 + {"data":...})
 *   asenkron program: 202 Accepted; mainframe cagrisi arka planda; sonuc
 *                     callbackUrl'e (BPM /api/avrasya/geri-donus/{pid}/{wid}) POST edilir
 * </pre>
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class MainframeGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainframeGatewayApplication.class, args);
    }
}
