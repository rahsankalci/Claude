package com.garantibbva.processarchitecture.converter.parse;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Kesik (ortasinda biten) XML'i iyilestirir: yarim kalan son etiketi atar, acik kalan
 * etiketleri sirayla kapatir. Yalnizca normal parse basarisiz olursa denenir.
 */
final class XmlRepair {
    private XmlRepair() {
    }

    static String repair(String xml) {
        Deque<String> stack = new ArrayDeque<>();
        int n = xml.length();
        int i = 0;
        int lastSafe = 0; // son tam islenmis konumun sonu
        while (i < n) {
            char c = xml.charAt(i);
            if (c != '<') {
                i++;
                continue;
            }
            if (xml.startsWith("<!--", i)) {
                int e = xml.indexOf("-->", i + 4);
                if (e < 0) {
                    break;
                }
                i = e + 3;
                lastSafe = i;
            } else if (xml.startsWith("<![CDATA[", i)) {
                int e = xml.indexOf("]]>", i + 9);
                if (e < 0) {
                    break;
                }
                i = e + 3;
                lastSafe = i;
            } else if (xml.startsWith("<?", i)) {
                int e = xml.indexOf("?>", i + 2);
                if (e < 0) {
                    break;
                }
                i = e + 2;
                lastSafe = i;
            } else {
                int e = tagEnd(xml, i);
                if (e < 0) {
                    break; // yarim kalmis etiket
                }
                String tag = xml.substring(i + 1, e); // '>' haric
                if (tag.startsWith("/")) {
                    if (!stack.isEmpty()) {
                        stack.pop();
                    }
                } else if (!tag.endsWith("/")) {
                    int sp = 0;
                    while (sp < tag.length() && !Character.isWhitespace(tag.charAt(sp))) {
                        sp++;
                    }
                    stack.push(tag.substring(0, sp));
                }
                i = e + 1;
                lastSafe = i;
            }
        }
        StringBuilder sb = new StringBuilder(xml.substring(0, Math.max(lastSafe, 0)));
        // etiketten sonra kalan yarim metin (<Tag>abc... ) cikarilir: lastSafe zaten etiket sonunda
        while (!stack.isEmpty()) {
            sb.append("</").append(stack.pop()).append('>');
        }
        return sb.toString();
    }

    /** '<' konumundan etiketin kapanis '>' indeksini verir (tirnak icindeki '>' atlanir). */
    private static int tagEnd(String s, int from) {
        char quote = 0;
        for (int i = from + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }
}
