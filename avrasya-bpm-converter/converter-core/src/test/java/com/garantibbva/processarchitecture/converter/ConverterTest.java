package com.garantibbva.processarchitecture.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class ConverterTest {

    private static Document xml(String s) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    /** BPMN butunlugu: kimlikler benzersiz, akislar var olan dugumlere bagli, jBPM'in giris kurallari saglanir. */
    private static void assertBpmnConsistent(String name, String bpmn) throws Exception {
        Document d = xml(bpmn);
        NodeList all = d.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "*");
        Set<String> ids = new HashSet<>();
        Map<String, String> tagOf = new HashMap<>();
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (e.hasAttribute("id") && e.getParentNode() != null && !"definitions".equals(e.getLocalName())) {
                String tag = e.getLocalName();
                if (tag.equals("sequenceFlow") || tag.endsWith("Event") || tag.endsWith("Gateway") || tag.endsWith("Task")
                        || tag.equals("task") || tag.equals("callActivity")) {
                    assertTrue(ids.add(e.getAttribute("id")), name + ": yinelenen id " + e.getAttribute("id"));
                    tagOf.put(e.getAttribute("id"), tag);
                }
            }
        }
        Map<String, Integer> in = new HashMap<>();
        Map<String, Integer> out = new HashMap<>();
        NodeList flows = d.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "sequenceFlow");
        assertTrue(flows.getLength() > 0, name + ": akis yok");
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            assertTrue(tagOf.containsKey(f.getAttribute("sourceRef")), name + ": kaynak yok " + f.getAttribute("sourceRef"));
            assertTrue(tagOf.containsKey(f.getAttribute("targetRef")), name + ": hedef yok " + f.getAttribute("targetRef"));
            in.merge(f.getAttribute("targetRef"), 1, Integer::sum);
            out.merge(f.getAttribute("sourceRef"), 1, Integer::sum);
        }
        for (Map.Entry<String, String> e : tagOf.entrySet()) {
            String id = e.getKey();
            String tag = e.getValue();
            int i = in.getOrDefault(id, 0);
            int o = out.getOrDefault(id, 0);
            if (tag.equals("sequenceFlow")) {
                continue;
            }
            if (tag.endsWith("Gateway")) {
                // jBPM: birlestirme kapisi >1 giris, dal kapisi >1 cikis ister
                assertTrue(i > 1 || o > 1, name + ": kapi " + id + " (giris " + i + ", cikis " + o + ")");
                assertFalse(i > 1 && o > 1, name + ": karma kapi " + id);
            } else if (tag.equals("startEvent")) {
                assertEquals(0, i, name + ": Start girisi var");
                assertTrue(o >= 1, name + ": Start cikissiz");
            } else {
                assertEquals(1, i, name + ": " + id + " (" + tag + ") giris sayisi");
                if (!tag.equals("endEvent")) {
                    assertEquals(1, o, name + ": " + id + " (" + tag + ") cikis sayisi");
                }
            }
        }
        // varsayilan akis kapinin cikislarindan biri olmali
        NodeList gws = d.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "exclusiveGateway");
        for (int i = 0; i < gws.getLength(); i++) {
            Element g = (Element) gws.item(i);
            if (g.hasAttribute("default")) {
                assertTrue(ids.contains(g.getAttribute("default")), name + ": default akis yok");
            }
        }
    }

    private ConversionResult convert(String res, ConverterOptions o) throws Exception {
        return new Converter(o).convert(ParserTest.resource(res));
    }

    @Test
    void ebrBasicTestProducesConsistentProject() throws Exception {
        ConversionResult r = convert("EBR_BASIC_TEST.avrasya.xml", new ConverterOptions());
        assertEquals("ebr-basic-test", r.artifactId);
        assertTrue(r.warnings.isEmpty(), r.warnings::toString);
        String bpmnKey = r.files.keySet().stream().filter(k -> k.endsWith("EBR_BASIC_TEST.bpmn")).findFirst().orElseThrow();
        assertTrue(bpmnKey.contains("com/garantibbva/processarchitecture/ebrbasictest/"));
        assertBpmnConsistent("EBR", r.files.get(bpmnKey));
        String b = r.files.get(bpmnKey);
        assertTrue(b.contains("drools:taskName=\"AvrasyaService\""));
        assertTrue(b.contains("WQ_585") && b.contains("WQ_1131"), "hata kuyrugu gruplari");
        assertTrue(b.contains("Surec.eq(Surec.v(c, \"sGK_sALL/sGK_sWIP/status_code\"), \"0\")"), "EndCondition cevrildi");
        assertTrue(b.contains("54866") && b.contains("269812"));
    }

    @Test
    void bigTruncatedProcessProducesBlocksAndHumanTasks() throws Exception {
        ConversionResult r = convert("IH_DAB_TANZIM_FC.avrasya.xml", new ConverterOptions());
        long bpmns = r.files.keySet().stream().filter(k -> k.endsWith(".bpmn")).count();
        assertEquals(5, bpmns);
        for (Map.Entry<String, String> e : r.files.entrySet()) {
            if (e.getKey().endsWith(".bpmn")) {
                assertBpmnConsistent(e.getKey(), e.getValue());
            }
        }
        String main = r.files.entrySet().stream().filter(e -> e.getKey().endsWith("/IH_DAB_TANZIM_FC.bpmn"))
                .findFirst().orElseThrow().getValue();
        assertTrue(main.contains("calledElement=\"IH_DAB_TANZIM_FC.ENCLAVE\""));
        String giris = r.files.entrySet().stream().filter(e -> e.getKey().endsWith("DAB_TANZIM_GIRIS.bpmn"))
                .findFirst().orElseThrow().getValue();
        assertTrue(giris.contains("<bpmn2:userTask id=\"Portfoy_Belirleme1\""));
        assertTrue(giris.contains("WQ_587"));
        assertTrue(giris.contains("uGrup"), "WorkQueueFromContainer -> degiskenden grup");
        assertTrue(giris.contains("Surec.set(c, \"sExport_sQUE/qKuyrukAdi\"") || giris.contains("sExport_sALL/sExport_sQUE/qKuyrukAdi"),
                "C esleme (qKuyrukAdi) script'te");
        assertTrue(r.warnings.stream().anyMatch(w -> w.contains("kesik")));
    }

    @Test
    void generatedProjectSkeletonIsComplete() throws Exception {
        ConversionResult r = convert("EBR_BASIC_TEST.avrasya.xml", new ConverterOptions());
        String pkg = "com/garantibbva/processarchitecture/ebrbasictest";
        for (String f : new String[] {"pom.xml", "CONVERSION_REPORT.md",
                "ebr-basic-test-kjar/pom.xml", "ebr-basic-test-service/pom.xml",
                "ebr-basic-test-kjar/src/main/java/" + pkg + "/model/AvrasyaContainer.java",
                "ebr-basic-test-kjar/src/main/java/" + pkg + "/surec/Surec.java",
                "ebr-basic-test-kjar/src/main/java/" + pkg + "/handler/AvrasyaServiceWorkItemHandler.java",
                "ebr-basic-test-kjar/src/main/java/" + pkg + "/handler/AvrasyaServiceConfig.java",
                "ebr-basic-test-kjar/src/main/resources/META-INF/kmodule.xml",
                "ebr-basic-test-kjar/src/main/resources/META-INF/kie-deployment-descriptor.xml",
                "ebr-basic-test-kjar/src/main/resources/" + pkg + "/AvrasyaService.wid",
                "ebr-basic-test-service/src/main/java/" + pkg + "/service/SurecEntegrasyonController.java",
                "ebr-basic-test-service/src/main/java/" + pkg + "/service/AvrasyaGeriDonusController.java",
                "ebr-basic-test-service/src/main/resources/application.properties",
                "ebr-basic-test-service/src/main/resources/META-INF/spring.factories"}) {
            assertTrue(r.files.containsKey(f), "eksik: " + f);
        }
        for (Map.Entry<String, String> e : r.files.entrySet()) {
            assertFalse(e.getValue().contains("@@"), "cozulmemis yer tutucu: " + e.getKey());
            if (e.getKey().endsWith(".xml") || e.getKey().endsWith(".bpmn")) {
                xml(e.getValue()); // iyi bicimli
            }
        }
        assertTrue(r.files.get("ebr-basic-test-service/src/main/resources/application.properties").contains("avrasya.service.program.269812"));
    }

    @Test
    void arkModeLinksPomsToArkBpmParentsNotKie() throws Exception {
        ConverterOptions o = new ConverterOptions();
        o.kjarParentArtifactId = "kjar-parent";
        o.serviceParentArtifactId = "ark-bpm-starter-parent";
        o.serviceDependencies = "ark-bpm-springboot-app, ark-bpm-springboot-common";
        ConversionResult r = convert("EBR_BASIC_TEST.avrasya.xml", o);
        String kjar = r.files.get("ebr-basic-test-kjar/pom.xml");
        String svc = r.files.get("ebr-basic-test-service/pom.xml");
        assertTrue(kjar.contains("<artifactId>kjar-parent</artifactId>"));
        assertTrue(svc.contains("<artifactId>ark-bpm-starter-parent</artifactId>"));
        assertTrue(svc.contains("ark-bpm-springboot-app") && svc.contains("ark-bpm-springboot-common"));
        assertFalse(kjar.contains("<kie.version>") || svc.contains("<kie.version>"), "KIE surumu dogrudan verilmemeli");
        assertFalse(svc.contains("kie-server-spring-boot-starter"), "KIE'ye dogrudan bagimlilik olmamali");
        xml(kjar);
        xml(svc);
    }

    @Test
    void directKieModeUsesKieVersion() throws Exception {
        ConverterOptions o = new ConverterOptions();
        o.useArkBpm = false;
        ConversionResult r = convert("EBR_BASIC_TEST.avrasya.xml", o);
        assertTrue(r.files.get("ebr-basic-test-kjar/pom.xml").contains("<kie.version>7.74.1.Final</kie.version>"));
        assertFalse(r.files.get("ebr-basic-test-kjar/pom.xml").contains("<parent>"));
    }

    @Test
    void customCoordinatesAndPackage() throws Exception {
        ConverterOptions o = new ConverterOptions();
        o.groupId = "tr.com.test";
        o.artifactId = "my-proc";
        o.basePackage = "tr.com.test.myproc";
        ConversionResult r = convert("EBR_BASIC_TEST.avrasya.xml", o);
        assertTrue(r.files.containsKey("my-proc-kjar/src/main/java/tr/com/test/myproc/model/AvrasyaContainer.java"));
        assertTrue(r.files.get("my-proc-kjar/pom.xml").contains("<groupId>tr.com.test</groupId>"));
    }

    @Test
    void writeToDiskStaysInsideOutputDir(@TempDir Path dir) throws Exception {
        ConversionResult r = convert("EBR_BASIC_TEST.avrasya.xml", new ConverterOptions());
        Path root = r.writeTo(dir);
        assertTrue(Files.isRegularFile(root.resolve("pom.xml")));
        assertTrue(Files.isRegularFile(root.resolve("ebr-basic-test-kjar/pom.xml")));
        assertTrue(root.startsWith(dir));
    }

    @Test
    void settingsRoundTrip(@TempDir Path dir) throws Exception {
        ConverterOptions o = new ConverterOptions();
        o.groupId = "x.y";
        o.serviceDependencies = "a,b";
        o.useArkBpm = false;
        Path f = dir.resolve("s.properties");
        try (var out = Files.newOutputStream(f)) {
            o.save(out);
        }
        ConverterOptions l = ConverterOptions.load(f);
        assertEquals("x.y", l.groupId);
        assertEquals("a,b", l.serviceDependencies);
        assertFalse(l.useArkBpm);
    }
}
