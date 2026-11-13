package com.garantibbva.processarchitecture.ihdabtanzimfc.service;

import org.kie.server.api.KieServerConstants;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.KieContainerStatus;
import org.kie.server.api.model.KieServerConfigItem;
import org.kie.server.api.model.ReleaseId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Uygulamanin calistirdigi KIE container'lari.
 *
 * <p>Iki kjar ({@code ih-dab-tanzim-fc-kjar}, {@code ebr-basic-test-kjar}) ayni classpath'te
 * oldugu icin ({@code kieserver.classPathContainer=true}) her ikisinin de default kbase'i gorunur.
 * {@code kieserver.deployments[n]} ile kbase secilemedigi icin container'lar burada
 * KBase / KSession / RuntimeStrategy config item'lariyla tanimlanir; KIE Server bu bean'leri
 * acilista baslatir (KieServerAutoConfiguration#kieServer(List&lt;KieContainerResource&gt;)).
 *
 * <p>Yeni bir Avrasya sureci eklendiginde: kjar'i pom'a ekleyin ve buraya bir bean ekleyin.
 */
@Configuration
public class KieContainerTanimlari {

    private static final String GROUP = "com.garantibbva.processarchitecture";

    @Value("${bpm.kjar-version}")
    private String version;

    @Bean
    public KieContainerResource ihDabTanzimFcContainer() {
        return container("ih-dab-tanzim-fc", "ih-dab-tanzim-fc-kjar", "ihDabTanzimFcKBase", "ihDabTanzimFcKSession");
    }

    @Bean
    public KieContainerResource ebrBasicTestContainer() {
        return container("ebr-basic-test", "ebr-basic-test-kjar", "ebrBasicTestKBase", "ebrBasicTestKSession");
    }

    private KieContainerResource container(String id, String artifactId, String kbase, String ksession) {
        ReleaseId rid = new ReleaseId(GROUP, artifactId, version);
        KieContainerResource c = new KieContainerResource(id, rid);
        c.setResolvedReleaseId(rid);
        c.setContainerAlias(id);
        c.setStatus(KieContainerStatus.STARTED);
        c.addConfigItem(new KieServerConfigItem(KieServerConstants.PCFG_KIE_BASE, kbase, String.class.getName()));
        c.addConfigItem(new KieServerConfigItem(KieServerConstants.PCFG_KIE_SESSION, ksession, String.class.getName()));
        c.addConfigItem(new KieServerConfigItem(KieServerConstants.PCFG_RUNTIME_STRATEGY, "PER_PROCESS_INSTANCE",
                String.class.getName()));
        return c;
    }
}
