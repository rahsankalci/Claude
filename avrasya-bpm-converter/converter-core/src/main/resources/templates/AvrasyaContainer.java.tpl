package @@pkg@@.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Avrasya surec konteyneri (@@containerRoot@@) - yol tabanli, ic ice Map.
 *
 * <p>Avrasya'daki {@code sExport_sALL/sExport_sWIP/return_code} gibi yollar {@link #get(String)} /
 * {@link #set(String, String)} ile okunur/yazilir. Kok adi ({@code @@containerRoot@@/}) yolda
 * verilse de verilmese de ayni alana gider. JSON gosterimi konteynerin kendisidir (kok adi olmadan):
 * <pre>{"sExport_sWIP":{"return_code":"00"}, ...}</pre>
 * Boylece servisler ve entegrasyon uclari konteyneri duz JSON olarak gonderip alir; bilinmeyen
 * dugumler kaybolmaz.
 */
public class AvrasyaContainer implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String ROOT = "@@containerRoot@@";

    private LinkedHashMap<String, Object> data = new LinkedHashMap<>();

    public AvrasyaContainer() {
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public AvrasyaContainer(Map<String, Object> data) {
        if (data != null) {
            this.data = deepCopy(data);
        }
    }

    @JsonValue
    public Map<String, Object> toMap() {
        return data;
    }

    /** Yoldaki deger (metin); yoksa {@code null}. */
    @SuppressWarnings("unchecked")
    public String get(String path) {
        Object cur = data;
        for (String seg : segments(path)) {
            if (!(cur instanceof Map)) {
                return null;
            }
            cur = ((Map<String, Object>) cur).get(seg);
        }
        return cur == null || cur instanceof Map ? null : String.valueOf(cur);
    }

    /** Yola deger yazar; ara dugumleri olusturur. {@code null} deger alani siler gibi bos birakir. */
    @SuppressWarnings("unchecked")
    public void set(String path, String value) {
        String[] segs = segments(path);
        if (segs.length == 0) {
            return;
        }
        Map<String, Object> cur = data;
        for (int i = 0; i < segs.length - 1; i++) {
            Object next = cur.get(segs[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                cur.put(segs[i], next);
            }
            cur = (Map<String, Object>) next;
        }
        cur.put(segs[segs.length - 1], value);
    }

    static String[] segments(String path) {
        if (path == null) {
            return new String[0];
        }
        String p = path.trim();
        if (p.equals(ROOT)) {
            return new String[0];
        }
        if (p.startsWith(ROOT + "/")) {
            p = p.substring(ROOT.length() + 1);
        }
        return p.isEmpty() ? new String[0] : p.split("/");
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> deepCopy(Map<String, Object> src) {
        LinkedHashMap<String, Object> res = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            Object v = e.getValue();
            res.put(e.getKey(), v instanceof Map ? deepCopy((Map<String, Object>) v) : v);
        }
        return res;
    }

    @Override
    public String toString() {
        return data.toString();
    }
}
