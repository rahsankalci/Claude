package com.garantibbva.processarchitecture.converter.parse;

import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Act;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Conn;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Mapping;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Scope;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Avrasya Workflow XML -> {@link AvrasyaProcess}. */
public final class AvrasyaParser {

    private final List<String> warnings = new ArrayList<>();

    public List<String> warnings() {
        return warnings;
    }

    public AvrasyaProcess parse(String xml) {
        Document doc;
        try {
            doc = dom(xml);
        } catch (Exception first) {
            try {
                doc = dom(XmlRepair.repair(xml));
                warnings.add("XML kesik/bozuk (" + first.getMessage().trim() + "). Dosya sonu otomatik kapatildi; "
                        + "eksik bolumler (baglantilar, son aktiviteler) donusumde yoktur.");
            } catch (Exception second) {
                throw new IllegalArgumentException("Avrasya XML okunamadi: " + first.getMessage(), first);
            }
        }
        Element root = doc.getDocumentElement();
        if (!"AvrasyaProcess".equals(root.getTagName())) {
            throw new IllegalArgumentException("Kok eleman <AvrasyaProcess> degil: <" + root.getTagName() + ">");
        }
        AvrasyaProcess p = new AvrasyaProcess();
        Element info = child(root, "ProcessInfo");
        if (info != null) {
            p.name = text(info, "ProcessName");
            p.description = text(info, "Description");
            p.documentation = text(info, "Documentation");
            p.aclId = text(info, "ACLID");
            p.categoryId = text(info, "CategoryID");
            p.containerId = text(info, "InputContainerID");
            Element attrs = child(info, "Attributes");
            if (attrs != null) {
                for (Element a : children(attrs, "Attribute")) {
                    String n = a.getAttribute("Name");
                    String ref = a.getAttribute("RefNode");
                    if (!n.isEmpty() && !ref.isEmpty()) {
                        p.attributes.put(n, ref);
                    }
                }
            }
        }
        if (p.name.isBlank()) {
            throw new IllegalArgumentException("ProcessName bulunamadi");
        }
        p.root = scope(p.name, child(root, "AllActivities"), child(root, "AllConnections"));
        return p;
    }

    // ---------------------------------------------------------------- kapsam

    private Scope scope(String name, Element allActivities, Element allConnections) {
        Scope s = new Scope(name);
        if (allActivities != null) {
            for (Element e : children(allActivities, "Activity")) {
                s.acts.add(activity(e, name));
            }
        }
        if (allConnections != null) {
            for (Element c : children(allConnections, "Connection")) {
                s.conns.add(new Conn(text(c, "SourceActivityID"), text(c, "DestinationActivityID"),
                        text(c, "Condition"), text(c, "Description")));
            }
        }
        if (s.conns.isEmpty() && s.acts.size() > 1) {
            inferConnections(s);
        }
        return s;
    }

    private Act activity(Element e, String parentName) {
        Act a = new Act();
        a.name = text(e, "ActivityName");
        a.type = text(e, "ActivityType");
        a.index = text(e, "ActivityIndex");
        a.programId = text(e, "ProgramID");
        a.startOption = text(e, "StartOption");
        a.startCondition = text(e, "StartCondition");
        String ec = text(e, "EndCondition");
        a.endCondition = ec.isBlank() ? "TRUE" : ec;
        a.wqOption = text(e, "WorkQueueOption");
        a.wqId = text(e, "WorkQueueID");
        a.wqFromContainer = text(e, "WorkQueueFromContainer");
        a.description = text(e, "Description");
        a.documentation = text(e, "Documentation");
        a.incoming = parseInt(text(e, "IncomingConnectorCount"), 0);
        Element loc = child(e, "Location");
        if (loc != null) {
            a.left = parseInt(loc.getAttribute("Left"), -1);
            a.top = parseInt(loc.getAttribute("Top"), -1);
        }
        Element dm = child(e, "DataMapping");
        if (dm != null) {
            for (Element m : children(dm, "Map")) {
                a.maps.add(new Mapping(m.getAttribute("MappingType"), m.getAttribute("SourceActivityID"),
                        m.getAttribute("SourceActivity"), m.getAttribute("SourceNodeName"),
                        m.getAttribute("DestinationNodeName"), m.getAttribute("ValueType")));
            }
        }
        Element bp = child(e, "BlockProcess");
        Element block = bp == null ? null : child(bp, "AvrasyaBlock");
        if (block != null) {
            a.block = scope(a.name, child(block, "AllActivities"), child(block, "AllConnections"));
            if (a.type.isBlank()) {
                a.type = "Block";
            }
        } else if ("Block".equalsIgnoreCase(a.type)) {
            warnings.add("Blok '" + a.name + "' icerigi XML'de yok (kesik dosya); bos alt surec uretilir.");
            a.block = new Scope(a.name);
        }
        if (a.index.isBlank()) {
            a.index = a.isStart() ? "0" : a.isEnd() ? "1" : a.name;
        }
        return a;
    }

    /**
     * AllConnections yoksa (kesik dosya) akis tahmin edilir:
     * <ol>
     *   <li>Aktivitenin girdi eslemelerindeki (NDM) kaynak aktivite -> bu aktivite.</li>
     *   <li>Kaynagi bilinmeyen aktivite, dokumandaki bir onceki aktiviteyi izler (Start'tan baslayan zincir).</li>
     *   <li>Cikisi olmayan aktivite End'e baglanir.</li>
     * </ol>
     * Declared {@code IncomingConnectorCount} ile uyusmayan aktiviteler uyari olarak bildirilir.
     */
    private void inferConnections(Scope s) {
        Set<String> seen = new HashSet<>();
        Act prev = s.start();
        Act end = null;
        for (Act a : s.acts) {
            if (a.isEnd()) {
                end = a;
            }
        }
        for (Act a : s.acts) {
            if (a.isStart()) {
                continue;
            }
            boolean any = false;
            for (Mapping m : a.maps) {
                if (!m.isInput() || m.srcActivityId.isBlank() || "-1".equals(m.srcActivityId)
                        || m.srcActivityId.equals(a.index)) {
                    continue;
                }
                Act src = s.byIndex(m.srcActivityId);
                if (src != null && !src.isEnd()) {
                    any = true;
                    if (seen.add(src.index + ">" + a.index)) {
                        s.conns.add(new Conn(src.index, a.index, "TRUE", ""));
                    }
                }
            }
            if (!any && !a.isEnd() && prev != null && seen.add(prev.index + ">" + a.index)) {
                s.conns.add(new Conn(prev.index, a.index, "TRUE", ""));
            }
            if (!a.isEnd()) {
                prev = a;
            }
        }
        // End'in kendi girdi eslemeleri (kaynak aktiviteler) gercek onculleridir; varsa ek End baglantisi uydurulmaz
        boolean endHasSources = false;
        for (Conn c : s.conns) {
            if (end != null && c.dst.equals(end.index)) {
                endHasSources = true;
            }
        }
        for (Act a : s.acts) {
            if (a.isEnd() || endHasSources) {
                continue;
            }
            boolean hasOut = false;
            for (Conn c : s.conns) {
                if (c.src.equals(a.index)) {
                    hasOut = true;
                    break;
                }
            }
            if (!hasOut && end != null && seen.add(a.index + ">" + end.index)) {
                s.conns.add(new Conn(a.index, end.index, "TRUE", ""));
            }
        }
        s.connectionsInferred = true;
        StringBuilder mismatch = new StringBuilder();
        for (Act a : s.acts) {
            if (a.isStart()) {
                continue;
            }
            int got = 0;
            for (Conn c : s.conns) {
                if (c.dst.equals(a.index)) {
                    got++;
                }
            }
            if (a.incoming > 0 && got != a.incoming) {
                mismatch.append(mismatch.length() > 0 ? ", " : "").append(a.name).append(" (beklenen ").append(a.incoming)
                        .append(", cikarilan ").append(got).append(')');
            }
        }
        warnings.add("'" + s.name + "' kapsaminda <AllConnections> yok; akis veri eslemelerinden ve aktivite sirasindan tahmin edildi, "
                + "kosullar TRUE sayildi. Tam XML ile tekrar donusturun ya da akisi elle dogrulayin."
                + (mismatch.length() > 0 ? " IncomingConnectorCount ile uyusmayanlar: " + mismatch + "." : ""));
    }

    // ---------------------------------------------------------------- DOM yardimcilari

    private static Document dom(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setNamespaceAware(false);
        DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        String s = xml.startsWith("﻿") ? xml.substring(1) : xml;
        return b.parse(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static Element child(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && tag.equals(((Element) n).getTagName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> res = new ArrayList<>();
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element && tag.equals(((Element) n).getTagName())) {
                res.add((Element) n);
            }
        }
        return res;
    }

    private static String text(Element parent, String tag) {
        Element e = child(parent, tag);
        return e == null ? "" : e.getTextContent().trim();
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
