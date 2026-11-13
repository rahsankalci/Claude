package com.garantibbva.processarchitecture.mainframegateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * z/OS Connect EE uzerinden CICS/IMS programi cagirir: konteyner JSON olarak
 * {@code gateway.programs.<id>.url} adresine POST edilir, donen JSON konteynerdir.
 * (z/OS Connect API'si program copybook'unu bu JSON'a esler.) Kimlik bilgisi gerekiyorsa
 * {@code ZOSCONNECT_AUTH_HEADER} ortam degiskeniyle verilir.
 */
@Component
@ConditionalOnProperty(name = "gateway.connector", havingValue = "zos-connect")
public class ZosConnectConnector implements MainframeConnector {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final GatewayProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public ZosConnectConnector(GatewayProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(props.getTimeoutMs())).build();
    }

    @Override
    public Map<String, Object> call(String programId, Map<String, Object> data) {
        GatewayProperties.Program p = props.getPrograms().get(programId);
        if (p == null || p.getUrl() == null) {
            throw new MainframeException("ProgramID " + programId + " icin gateway.programs." + programId + ".url tanimli degil");
        }
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(p.getUrl()))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(data)));
            String auth = System.getenv("ZOSCONNECT_AUTH_HEADER");
            if (auth != null && !auth.isBlank()) {
                b.header("Authorization", auth);
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) {
                throw new MainframeException("z/OS Connect HTTP " + r.statusCode() + ": " + r.body());
            }
            return mapper.readValue(r.body(), MAP);
        } catch (MainframeException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MainframeException("Mainframe cagrisi kesildi", e);
        } catch (Exception e) {
            throw new MainframeException("Mainframe'e ulasilamadi: " + e.getMessage(), e);
        }
    }
}
