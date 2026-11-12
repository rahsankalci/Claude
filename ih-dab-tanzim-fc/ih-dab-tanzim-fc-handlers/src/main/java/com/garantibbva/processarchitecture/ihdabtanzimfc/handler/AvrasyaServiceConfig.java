package com.garantibbva.processarchitecture.ihdabtanzimfc.handler;

import java.time.Duration;
import java.util.Locale;
import java.util.function.Function;

/**
 * Avrasya ProgramID -> REST endpoint cozumlemesi.
 *
 * <p>Oncelik sirasi (ilk bulunan kullanilir):
 * <ol>
 *   <li>System property {@code avrasya.service.program.<ProgramID>.url}</li>
 *   <li>Ortam degiskeni {@code AVRASYA_SERVICE_PROGRAM_<ProgramID>_URL}</li>
 *   <li>{@code avrasya.service.base-url} / {@code AVRASYA_SERVICE_BASE_URL} + "/" + ProgramID</li>
 * </ol>
 *
 * <p>Diger ayarlar:
 * <ul>
 *   <li>{@code avrasya.service.timeout-ms} / {@code AVRASYA_SERVICE_TIMEOUT_MS} (varsayilan 30000)</li>
 *   <li>{@code avrasya.service.auth-header} / {@code AVRASYA_SERVICE_AUTH_HEADER}:
 *       verilirse {@code Authorization} basligi olarak gonderilir (orn. "Bearer xyz").
 *       Degeri OpenShift Secret'tan ortam degiskeni olarak verin, koda/properties'e yazmayin.</li>
 * </ul>
 * Spring Boot'ta application.properties'teki ayni anahtarlar, uygulama acilisinda
 * System property'ye kopyalanir (bkz. service modulu: AvrasyaServiceProperties).
 */
public class AvrasyaServiceConfig {

    public static final String PREFIX = "avrasya.service.";

    private final Function<String, String> lookup;

    public AvrasyaServiceConfig(Function<String, String> lookup) {
        this.lookup = lookup;
    }

    /** System property + ortam degiskeninden okuyan varsayilan yapilandirma. */
    public static AvrasyaServiceConfig fromEnvironment() {
        return new AvrasyaServiceConfig(key -> {
            String v = System.getProperty(key);
            if (v == null || v.isBlank()) {
                v = System.getenv(toEnvName(key));
            }
            return (v == null || v.isBlank()) ? null : v.trim();
        });
    }

    static String toEnvName(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    public String resolveUrl(String programId) {
        String specific = lookup.apply(PREFIX + "program." + programId + ".url");
        if (specific != null) {
            return specific;
        }
        String base = lookup.apply(PREFIX + "base-url");
        if (base == null) {
            throw new IllegalStateException("ProgramID " + programId + " icin servis adresi tanimli degil. '"
                    + PREFIX + "program." + programId + ".url' veya '" + PREFIX + "base-url' ayarlayin.");
        }
        return base.endsWith("/") ? base + programId : base + "/" + programId;
    }

    public Duration timeout() {
        String v = lookup.apply(PREFIX + "timeout-ms");
        return Duration.ofMillis(v == null ? 30_000L : Long.parseLong(v));
    }

    public String authHeader() {
        return lookup.apply(PREFIX + "auth-header");
    }
}
