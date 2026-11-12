package com.garantibbva.processarchitecture.ihdabtanzimfc.service;

import com.garantibbva.processarchitecture.ihdabtanzimfc.handler.AvrasyaServiceConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * application.properties / ortam degiskenlerindeki {@code avrasya.service.*} ayarlarini
 * System property'ye tasir. AvrasyaServiceWorkItemHandler deployment descriptor'dan
 * (mvel) olusturuldugu icin Spring bean'i degildir; ayarlari System property'den okur.
 * Boylece ayni handler KIE Server (EAP/WildFly) uzerinde -D parametreleriyle de calisir.
 */
public class AvrasyaServiceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        for (PropertySource<?> ps : env.getPropertySources()) {
            if (!(ps instanceof EnumerablePropertySource)) {
                continue;
            }
            for (String name : ((EnumerablePropertySource<?>) ps).getPropertyNames()) {
                if (name.startsWith(AvrasyaServiceConfig.PREFIX) && System.getProperty(name) == null) {
                    try {
                        String value = env.getProperty(name);
                        if (value != null && !value.isBlank()) {
                            System.setProperty(name, value);
                        }
                    } catch (IllegalArgumentException unresolvedPlaceholder) {
                        // ${...} cozulemedi: handler ortam degiskenine bakar
                    }
                }
            }
        }
    }
}
