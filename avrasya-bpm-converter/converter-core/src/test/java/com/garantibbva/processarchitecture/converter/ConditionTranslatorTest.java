package com.garantibbva.processarchitecture.converter;

import static org.junit.jupiter.api.Assertions.*;

import com.garantibbva.processarchitecture.converter.gen.ConditionTranslator;
import org.junit.jupiter.api.Test;

class ConditionTranslatorTest {

    private static String t(String src) {
        ConditionTranslator.Result r = ConditionTranslator.translate(src, "c");
        assertTrue(r.ok, () -> src + " -> " + r.problem);
        return r.java;
    }

    @Test
    void trueFalse() {
        assertTrue(ConditionTranslator.translate("TRUE", "c").alwaysTrue);
        assertTrue(ConditionTranslator.translate("  ", "c").alwaysTrue);
        assertEquals("false", t("FALSE"));
    }

    @Test
    void equality() {
        assertEquals("Surec.eq(Surec.v(c, \"sExport_sALL/sExport_sWIP/return_code\"), \"00\")",
                t("(\"<%sExport_sALL/sExport_sWIP/return_code%>\"=\"00\")"));
    }

    @Test
    void notEqualsEmpty() {
        assertEquals("Surec.ne(Surec.v(c, \"a/b\"), \"\")", t("(\"<%a/b%>\"!=\"\")"));
        assertEquals("Surec.ne(Surec.v(c, \"a/b\"), \"\")", t("\"<%a/b%>\" <> \"\""));
    }

    @Test
    void andOrNotAndPrecedence() {
        assertEquals("(Surec.eq(Surec.v(c, \"a\"), \"1\") && Surec.eq(Surec.v(c, \"b\"), \"2\"))",
                t("\"<%a%>\"=\"1\" AND \"<%b%>\"=\"2\""));
        assertEquals("(Surec.eq(Surec.v(c, \"a\"), \"1\") || (Surec.eq(Surec.v(c, \"b\"), \"2\") && Surec.eq(Surec.v(c, \"d\"), \"3\")))",
                t("\"<%a%>\"=\"1\" OR \"<%b%>\"=\"2\" AND \"<%d%>\"=\"3\""));
        assertEquals("!Surec.eq(Surec.v(c, \"a\"), \"1\")", t("NOT \"<%a%>\"=\"1\""));
    }

    @Test
    void groupingAndNumericCompare() {
        assertEquals("((Surec.cmp(Surec.v(c, \"a\"), \"5\") > 0) || Surec.eq(Surec.v(c, \"b\"), \"x\"))",
                t("(\"<%a%>\" > \"5\" OR \"<%b%>\" = \"x\")"));
    }

    @Test
    void stringInterpolation() {
        assertEquals("Surec.eq((\"P_\" + Surec.v(c, \"a\")), \"P_1\")", t("\"P_<%a%>\"=\"P_1\""));
    }

    @Test
    void quotesAndBackslashesAreEscaped() {
        assertEquals("Surec.eq(Surec.v(c, \"a\"), \"x\\\\y\")", t("\"<%a%>\"=\"x\\y\""));
    }

    @Test
    void unparseableFallsBackToTrueWithProblem() {
        ConditionTranslator.Result r = ConditionTranslator.translate("\"<%a%>\" = ", "c");
        assertFalse(r.ok);
        assertEquals("true", r.java);
        assertNotNull(r.problem);
        assertFalse(ConditionTranslator.translate("\"abc", "c").ok);
    }
}
