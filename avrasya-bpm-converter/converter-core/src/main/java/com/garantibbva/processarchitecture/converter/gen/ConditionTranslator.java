package com.garantibbva.processarchitecture.converter.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * Avrasya kosul / EndCondition ifadesini Java boolean ifadesine cevirir.
 *
 * <pre>
 *   ("&lt;%sExport_sALL/sExport_sWIP/return_code%&gt;"="00")      ->  Surec.eq(Surec.v(c,"sExport_sALL/sExport_sWIP/return_code"), "00")
 *   ("&lt;%a%&gt;"!="" AND "&lt;%b%&gt;"="Y")                      ->  Surec.ne(...) &amp;&amp; Surec.eq(...)
 *   TRUE / FALSE
 * </pre>
 * Desteklenen: {@code = != <> < > <= >=}, {@code AND/OR/VE/VEYA/&&/||}, {@code NOT}, parantez,
 * {@code "metin"}, {@code &lt;%yol%&gt;} (tirnak icinde ya da disinda), sayilar, TRUE/FALSE.
 * Anlasilamayan ifade icin {@code ok=false} doner ve yerine {@code true} yazilir.
 */
public final class ConditionTranslator {

    public static final class Result {
        public final String java;
        public final boolean ok;
        public final boolean alwaysTrue;
        public final String problem;

        Result(String java, boolean ok, boolean alwaysTrue, String problem) {
            this.java = java;
            this.ok = ok;
            this.alwaysTrue = alwaysTrue;
            this.problem = problem;
        }
    }

    private final String containerVar;
    private final List<String> tokens = new ArrayList<>();
    private int pos;

    private ConditionTranslator(String containerVar) {
        this.containerVar = containerVar;
    }

    /** @param containerVar Java kodunda container degiskeninin adi (ornegin {@code c}). */
    public static Result translate(String avrasya, String containerVar) {
        String src = avrasya == null ? "" : avrasya.trim();
        if (src.isEmpty() || src.equalsIgnoreCase("TRUE")) {
            return new Result("true", true, true, null);
        }
        if (src.equalsIgnoreCase("FALSE")) {
            return new Result("false", true, false, null);
        }
        ConditionTranslator t = new ConditionTranslator(containerVar);
        try {
            t.tokenize(src);
            String java = t.parseOr();
            if (t.pos != t.tokens.size()) {
                throw new IllegalArgumentException("beklenmeyen '" + t.tokens.get(t.pos) + "'");
            }
            return new Result(java, true, false, null);
        } catch (RuntimeException e) {
            return new Result("true", false, true, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- lexer

    private void tokenize(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"') {
                int e = s.indexOf('"', i + 1);
                if (e < 0) {
                    throw new IllegalArgumentException("kapanmamis tirnak");
                }
                tokens.add(s.substring(i, e + 1));
                i = e + 1;
            } else if (s.startsWith("<%", i)) {
                int e = s.indexOf("%>", i + 2);
                if (e < 0) {
                    throw new IllegalArgumentException("kapanmamis <%");
                }
                tokens.add(s.substring(i, e + 2));
                i = e + 2;
            } else if (s.startsWith("!=", i) || s.startsWith("<>", i) || s.startsWith("<=", i)
                    || s.startsWith(">=", i) || s.startsWith("&&", i) || s.startsWith("||", i)) {
                tokens.add(s.substring(i, i + 2));
                i += 2;
            } else if (c == '=' || c == '<' || c == '>' || c == '(' || c == ')' || c == '!') {
                tokens.add(String.valueOf(c));
                i++;
            } else {
                int e = i;
                while (e < s.length() && !Character.isWhitespace(s.charAt(e)) && "=<>()!\"".indexOf(s.charAt(e)) < 0) {
                    e++;
                }
                if (e == i) {
                    throw new IllegalArgumentException("taninmayan karakter '" + c + "'");
                }
                tokens.add(s.substring(i, e));
                i = e;
            }
        }
    }

    // ---------------------------------------------------------------- parser

    private String peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private boolean isWord(String t, String... words) {
        if (t == null) {
            return false;
        }
        for (String w : words) {
            if (t.equalsIgnoreCase(w)) {
                return true;
            }
        }
        return false;
    }

    private String parseOr() {
        String left = parseAnd();
        while (isWord(peek(), "OR", "VEYA", "||")) {
            pos++;
            left = "(" + left + " || " + parseAnd() + ")";
        }
        return left;
    }

    private String parseAnd() {
        String left = parseNot();
        while (isWord(peek(), "AND", "VE", "&&")) {
            pos++;
            left = "(" + left + " && " + parseNot() + ")";
        }
        return left;
    }

    private String parseNot() {
        if (isWord(peek(), "NOT", "!") ) {
            pos++;
            return "!" + parseNot();
        }
        return parseCmp();
    }

    private String parseCmp() {
        if ("(".equals(peek())) {
            // parantez ya mantiksal grup ya da karsilastirmanin soldaki degeri olabilir; once grup dene
            int save = pos;
            pos++;
            String inner = parseOr();
            if (")".equals(peek())) {
                pos++;
                String op = peek();
                if (!isCmpOp(op)) {
                    return inner;
                }
            }
            pos = save; // degerin parantezi: asagida value() halleder
        }
        String left = value();
        String op = peek();
        if (!isCmpOp(op)) {
            // tek basina deger: bos olmayan / "true" kabul edilir
            return "Surec.truthy(" + left + ")";
        }
        pos++;
        String right = value();
        switch (op) {
            case "=":
                return "Surec.eq(" + left + ", " + right + ")";
            case "!=":
            case "<>":
                return "Surec.ne(" + left + ", " + right + ")";
            case "<":
                return "(Surec.cmp(" + left + ", " + right + ") < 0)";
            case ">":
                return "(Surec.cmp(" + left + ", " + right + ") > 0)";
            case "<=":
                return "(Surec.cmp(" + left + ", " + right + ") <= 0)";
            default:
                return "(Surec.cmp(" + left + ", " + right + ") >= 0)";
        }
    }

    private static boolean isCmpOp(String t) {
        return "=".equals(t) || "!=".equals(t) || "<>".equals(t) || "<".equals(t) || ">".equals(t)
                || "<=".equals(t) || ">=".equals(t);
    }

    private String value() {
        String t = peek();
        if (t == null) {
            throw new IllegalArgumentException("deger bekleniyordu");
        }
        pos++;
        if ("(".equals(t)) {
            String v = value();
            if (!")".equals(peek())) {
                throw new IllegalArgumentException("')' bekleniyordu");
            }
            pos++;
            return v;
        }
        if (t.startsWith("\"")) {
            return stringExpr(t.substring(1, t.length() - 1));
        }
        if (t.startsWith("<%")) {
            return path(t.substring(2, t.length() - 2));
        }
        if (t.equalsIgnoreCase("TRUE") || t.equalsIgnoreCase("FALSE")) {
            return "\"" + t.toLowerCase() + "\"";
        }
        if (t.matches("-?\\d+(\\.\\d+)?")) {
            return "\"" + t + "\"";
        }
        throw new IllegalArgumentException("taninmayan deger '" + t + "'");
    }

    private String path(String p) {
        return "Surec.v(" + containerVar + ", " + javaString(p.trim()) + ")";
    }

    /** "abc<%x%>def" -> "abc" + Surec.v(c,"x") + "def" */
    private String stringExpr(String s) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int b = s.indexOf("<%", i);
            if (b < 0) {
                parts.add(javaString(s.substring(i)));
                break;
            }
            int e = s.indexOf("%>", b + 2);
            if (e < 0) {
                throw new IllegalArgumentException("kapanmamis <%");
            }
            if (b > i) {
                parts.add(javaString(s.substring(i, b)));
            }
            parts.add(path(s.substring(b + 2, e)));
            i = e + 2;
        }
        if (parts.isEmpty()) {
            return "\"\"";
        }
        return parts.size() == 1 ? parts.get(0) : "(" + String.join(" + ", parts) + ")";
    }

    static String javaString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                default: sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
