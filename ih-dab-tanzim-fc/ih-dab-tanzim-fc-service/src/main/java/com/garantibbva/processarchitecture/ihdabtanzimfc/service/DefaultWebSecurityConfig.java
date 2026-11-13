package com.garantibbva.processarchitecture.ihdabtanzimfc.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Guvenlik. jBPM, Spring Security yetkilerini (authority) gorev GRUPLARI olarak kullanir
 * (SpringSecurityUserGroupCallback): bir kullanicinin "WQ_587" yetkisi varsa
 * Portfoy Belirleme1 gorevlerini gorur.
 *
 * <p>Gelistirme ortami ({@code openshift} profili DISINDA) bellek-ici kullanicilar
 * tanimlanir. OpenShift'te kurumsal kimlik saglayicisi (LDAP/Keycloak/OIDC) baglanmali;
 * o profil icin bu sinif kullanici tanimlamaz.
 */
@Configuration("kieServerSecurity")
@EnableWebSecurity
public class DefaultWebSecurityConfig extends WebSecurityConfigurerAdapter {

    static final String[] KUYRUKLAR = {"WQ_585", "WQ_587", "WQ_742", "WQ_1361", "WQ_UYUM", "WQ_1131"};

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors().and().csrf().disable()
                .authorizeRequests()
                .antMatchers("/actuator/health/**").permitAll()
                .antMatchers("/api/avrasya/geri-donus/**").hasAnyAuthority("mainframe", "admin")
                .antMatchers("/rest/**", "/api/**").authenticated()
                .and().httpBasic();
    }

    @Bean
    @Profile("!openshift")
    public UserDetailsService gelistirmeKullanicilari() {
        InMemoryUserDetailsManager m = new InMemoryUserDetailsManager();
        m.createUser(User.withUsername("wbadmin").password("{noop}wbadmin")
                .authorities("admin", "kie-server", "rest-all", "Administrators").build());
        // Entegrasyon kullanicisi: Fircosoft / World-Check / zamanlayici / sube sistemi geri cagrilari.
        // Bekleyen gorevi tamamlayabilmesi icin ilgili kuyruk gruplarinda olmali; portfoy
        // kuyruklari (qKuyrukAdi) dinamik oldugu icin gelistirmede ornek "PRT*" gruplari eklendi.
        m.createUser(User.withUsername("entegrasyon").password("{noop}entegrasyon")
                .authorities(concat(KUYRUKLAR, "kie-server", "PRT", "PRT_GIRIS", "PRT_YO", "PRT_SB", "PRT_IV")).build());
        // Mainframe / gateway teknik kullanicisi: yalnizca geri donus ucunu cagirir
        m.createUser(User.withUsername("mainframe").password("{noop}mainframe")
                .authorities("kie-server", "mainframe").build());
        m.createUser(User.withUsername("ali").password("{noop}ali")
                .authorities(concat(KUYRUKLAR, "kie-server", "PRT", "PRT_GIRIS", "PRT_YO", "PRT_SB", "PRT_IV")).build());
        return m;
    }

    private static String[] concat(String[] a, String... b) {
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
