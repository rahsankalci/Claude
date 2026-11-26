package com.garantibbva.processarchitecture.converter;

import static org.junit.jupiter.api.Assertions.*;

import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Act;
import com.garantibbva.processarchitecture.converter.parse.AvrasyaParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ParserTest {

    static String resource(String name) throws Exception {
        try (InputStream in = ParserTest.class.getResourceAsStream("/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void completeXml() throws Exception {
        AvrasyaParser parser = new AvrasyaParser();
        AvrasyaProcess p = parser.parse(resource("EBR_BASIC_TEST.avrasya.xml"));
        assertEquals("EBR_BASIC_TEST", p.name);
        assertEquals("11566", p.aclId);
        assertEquals(4, p.root.acts.size());
        assertEquals(3, p.root.conns.size());
        assertFalse(p.root.connectionsInferred);
        assertTrue(parser.warnings().isEmpty());
        Act ref = p.root.acts.stream().filter(a -> a.name.startsWith("Referans")).findFirst().orElseThrow();
        assertEquals("54866", ref.programId);
        assertFalse(ref.isManual());
        assertEquals("585", ref.wqId);
        assertEquals(3, ref.maps.size());
    }

    @Test
    void truncatedXmlIsRepairedAndReported() throws Exception {
        AvrasyaParser parser = new AvrasyaParser();
        AvrasyaProcess p = parser.parse(resource("IH_DAB_TANZIM_FC.avrasya.xml"));
        assertEquals("IH_DAB_TANZIM_FC", p.name);
        assertTrue(parser.warnings().get(0).contains("kesik"));
        Act enclave = p.root.acts.stream().filter(a -> a.name.equals("ENCLAVE")).findFirst().orElseThrow();
        assertTrue(enclave.isBlock());
        assertNotNull(enclave.block);
        // Kokte baglanti yok: Start -> Referans -> ENCLAVE -> End tahmin edilir
        assertTrue(p.root.connectionsInferred);
        assertEquals(3, p.root.conns.size());
    }

    @Test
    void rejectsNonAvrasyaXml() {
        assertThrows(IllegalArgumentException.class, () -> new AvrasyaParser().parse("<foo/>"));
        assertThrows(IllegalArgumentException.class, () -> new AvrasyaParser().parse("bu xml degil"));
    }

    @Test
    void doctypeIsRejected() {
        String evil = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><AvrasyaProcess>&e;</AvrasyaProcess>";
        assertThrows(IllegalArgumentException.class, () -> new AvrasyaParser().parse(evil));
    }
}
