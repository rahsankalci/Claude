package @@pkg@@.surec;

import @@pkg@@.model.AvrasyaContainer;
import org.kie.api.runtime.process.ProcessContext;

/**
 * @@processName@@ BPMN script / kosul yardimcilari (Avrasya kosul ve esleme dilinin karsiligi).
 * Dönüştürücü tarafindan uretilir; BPMN'deki ifadeler bu sinifi cagirir.
 */
public final class Surec {

    public static final String ATLA = "ATLA";
    public static final String GRUP_VARSAYILAN = "@@defaultGroup@@";
    public static final String GRUP_HATA = "@@errorGroup@@";

    private Surec() {
    }

    /** Surec degiskeni {@code container}; yoksa bos olusturulur. */
    public static AvrasyaContainer container(ProcessContext kcontext) {
        Object o = kcontext.getVariable("container");
        return o instanceof AvrasyaContainer ? (AvrasyaContainer) o : new AvrasyaContainer();
    }

    /** Yoldaki deger; yoksa bos metin (Avrasya'da bos alan "" ile karsilastirilir). */
    public static String v(AvrasyaContainer c, String path) {
        String s = c == null ? null : c.get(path);
        return s == null ? "" : s;
    }

    public static void set(AvrasyaContainer c, String path, String value) {
        c.set(path, value == null ? "" : value);
    }

    public static void copy(AvrasyaContainer c, String srcPath, String dstPath) {
        c.set(dstPath, v(c, srcPath));
    }

    public static boolean eq(String a, String b) {
        return norm(a).equals(norm(b));
    }

    public static boolean ne(String a, String b) {
        return !eq(a, b);
    }

    /** Iki deger de sayi ise sayisal, degilse metin karsilastirmasi. */
    public static int cmp(String a, String b) {
        String x = norm(a);
        String y = norm(b);
        try {
            return new java.math.BigDecimal(x).compareTo(new java.math.BigDecimal(y));
        } catch (NumberFormatException e) {
            return x.compareTo(y);
        }
    }

    /** Tek basina deger kosulu: bos degil ve "false"/"N"/"0" degil. */
    public static boolean truthy(String a) {
        String x = norm(a);
        return !x.isEmpty() && !x.equalsIgnoreCase("false") && !x.equals("N") && !x.equals("0");
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }

    /** Otomatik aktivite bitis kosulu: servis hatasiz doner VE Avrasya EndCondition saglanir. */
    public static boolean servisTamam(String serviceError, boolean endCondition) {
        return serviceError == null && endCondition;
    }

    public static boolean hataAtla(String karar) {
        return karar != null && ATLA.equalsIgnoreCase(karar.trim());
    }

    /** Kuyruk adi (grup) bossa varsayilan gruba duser. */
    public static String grup(String g) {
        return g == null || g.isBlank() ? GRUP_VARSAYILAN : g.trim();
    }
}
