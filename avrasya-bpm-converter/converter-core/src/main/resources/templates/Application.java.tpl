package @@pkg@@.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @@processName@@ calisma zamani (ark-bpm-springboot tabanli).
 * <ul>
 *   <li>Entegrasyon: {@code /api/@@slug@@/...} (bkz. {@link SurecEntegrasyonController})</li>
 *   <li>Mainframe geri donusu: {@code /api/avrasya/geri-donus/...} (bkz. {@link AvrasyaGeriDonusController})</li>
 * </ul>
 */
@SpringBootApplication
public class @@className@@Application {

    public static void main(String[] args) {
        SpringApplication.run(@@className@@Application.class, args);
    }
}
