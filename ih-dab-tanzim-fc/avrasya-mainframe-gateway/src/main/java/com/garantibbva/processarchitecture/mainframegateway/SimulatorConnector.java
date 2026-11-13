package com.garantibbva.processarchitecture.mainframegateway;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Gelistirme/test icin mainframe simulatoru: programlarin basari donus kodlarini uretir.
 * <ul>
 *   <li>54866, 116093 (Referans Process Aciklama Ekle): {@code sWIP/status_code = "0"}</li>
 *   <li>269812 (WebClient): {@code sGK_sOUT} doldurur, {@code return_code = "00"}</li>
 *   <li>diger programlar: {@code sWIP/return_code = "00"}</li>
 *   <li>konteynerde {@code "simule_hata"} alani varsa {@link MainframeException} (abend) firlatir</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "gateway.connector", havingValue = "simulator", matchIfMissing = true)
public class SimulatorConnector implements MainframeConnector {

    private static final Logger log = LoggerFactory.getLogger(SimulatorConnector.class);

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> call(String programId, Map<String, Object> data) {
        log.info("[SIMULATOR] mainframe programi calisiyor: {}", programId);
        if (data.containsKey("simule_hata")) {
            throw new MainframeException("CICS ABEND ASRA (simule) - program " + programId);
        }
        Map<String, Object> out = new LinkedHashMap<>(data);
        // EBR_BASIC_TEST konteyneri sGK_sALL, IH_DAB_TANZIM_FC konteyneri sExport_sALL
        boolean gk = out.containsKey("sGK_sWIP") || "54866".equals(programId) || "269812".equals(programId);
        String wipKey = gk ? "sGK_sWIP" : "sExport_sWIP";
        Map<String, Object> wip = new LinkedHashMap<>((Map<String, Object>) out.getOrDefault(wipKey, Map.of()));
        switch (programId) {
            case "54866":
            case "116093":
                wip.put("status_code", "0");
                break;
            case "269812":
                wip.put("return_code", "00");
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("mf_sonuc", "OK");
                o.put("mf_zaman", LocalDateTime.now().toString());
                out.put("sGK_sOUT", o);
                break;
            default:
                wip.put("return_code", "00");
        }
        out.put(wipKey, wip);
        return out;
    }
}
