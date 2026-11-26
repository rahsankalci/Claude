package com.garantibbva.processarchitecture.converter;

import java.util.Locale;
import java.util.Set;

/** Ad donusumleri (Turkce karakterler, XML ID, Java sinif adi, kebab-case). */
public final class Names {
    private Names() {
    }

    public static String ascii(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case 'ı': sb.append('i'); break;
                case 'İ': sb.append('I'); break;
                case 'ş': sb.append('s'); break;
                case 'Ş': sb.append('S'); break;
                case 'ğ': sb.append('g'); break;
                case 'Ğ': sb.append('G'); break;
                case 'ü': sb.append('u'); break;
                case 'Ü': sb.append('U'); break;
                case 'ö': sb.append('o'); break;
                case 'Ö': sb.append('O'); break;
                case 'ç': sb.append('c'); break;
                case 'Ç': sb.append('C'); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /** XML ID / BPMN eleman kimligi: harf/rakam/alt cizgi; rakamla baslamaz. */
    public static String xmlId(String s) {
        String a = ascii(s).replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (a.isEmpty()) {
            a = "N";
        }
        return Character.isDigit(a.charAt(0)) ? "N" + a : a;
    }

    /** Benzersiz kimlik (ayni ad tekrar ederse _2, _3 ...). */
    public static String unique(String base, Set<String> used) {
        String id = base;
        int n = 2;
        while (!used.add(id)) {
            id = base + "_" + n++;
        }
        return id;
    }

    /** IH_DAB_TANZIM_FC -> ih-dab-tanzim-fc */
    public static String kebab(String s) {
        String a = ascii(s).replaceAll("([a-z0-9])([A-Z])", "$1-$2").replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return a.toLowerCase(Locale.ROOT);
    }

    /** IH_DAB_TANZIM_FC -> IhDabTanzimFc */
    public static String pascal(String s) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebab(s).split("-")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        String r = sb.toString();
        return r.isEmpty() ? "Surec" : Character.isDigit(r.charAt(0)) ? "S" + r : r;
    }

    /** IH_DAB_TANZIM_FC -> ihdabtanzimfc (Java paket parcasi) */
    public static String packagePart(String s) {
        String r = kebab(s).replace("-", "");
        return r.isEmpty() ? "surec" : Character.isDigit(r.charAt(0)) ? "s" + r : r;
    }

    public static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** CDATA icine guvenle yazilacak metin. */
    public static String cdata(String s) {
        return "<![CDATA[" + s.replace("]]>", "]]]]><![CDATA[>") + "]]>";
    }
}
