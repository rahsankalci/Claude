package com.garantibbva.processarchitecture.converter.gen;

import static com.garantibbva.processarchitecture.converter.Names.cdata;
import static com.garantibbva.processarchitecture.converter.Names.xmlEscape;

import com.garantibbva.processarchitecture.converter.ConverterOptions;
import com.garantibbva.processarchitecture.converter.Names;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Act;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Conn;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Mapping;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Scope;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Avrasya surecini jBPM 7 / BAMOE 8 BPMN2 dosyalarina cevirir. Her Avrasya blogu ayri surec olur
 * ve call activity ile cagrilir.
 *
 * <ul>
 *   <li>StartOption=A (otomatik): {@code AvrasyaService} servis gorevi + EndCondition kontrolu +
 *       hata kuyrugu gorevi (Tekrar Dene / Atla)</li>
 *   <li>StartOption=M (manuel): insan gorevi (WorkQueue -&gt; GroupId), EndCondition saglanmazsa gorev yeniden acilir</li>
 *   <li>Block: call activity (ayri BPMN)</li>
 *   <li>Baglantilar: tek cikis duz akis; cok cikis hepsi TRUE ise paralel, degilse kosullu (XOR)</li>
 * </ul>
 */
public final class BpmnGenerator {

    private static final String CONTAINER_VAR = "container";
    private static final String CONTAINER_TYPE_ITEM = "_containerItem";

    private final ConverterOptions opt;
    private final AvrasyaProcess proc;
    private final String pkg;
    private final String containerRoot;
    private final List<String> warnings;
    private final Map<String, String> bpmn = new LinkedHashMap<>();
    private final Set<String> processIds = new HashSet<>();
    private final Set<String> programIds = new java.util.TreeSet<>();
    private final Map<String, String> programNames = new LinkedHashMap<>();
    private int humanTasks;
    private int serviceTasks;

    public BpmnGenerator(ConverterOptions opt, AvrasyaProcess proc, String pkg, String containerRoot, List<String> warnings) {
        this.opt = opt;
        this.proc = proc;
        this.pkg = pkg;
        this.containerRoot = containerRoot;
        this.warnings = warnings;
    }

    /** @return surec kimligi -> BPMN XML (ilki ana surec). */
    public Map<String, String> generate() {
        processIds.add(proc.name);
        scope(proc.root, proc.name, proc.name, proc.description.isBlank() ? proc.documentation : proc.description,
                "Avrasya " + proc.name + " (ACLID " + proc.aclId + ", CategoryID " + proc.categoryId + ", konteyner "
                        + proc.containerId + ")");
        return bpmn;
    }

    public Set<String> programIds() {
        return programIds;
    }

    public Map<String, String> programNames() {
        return programNames;
    }

    public int humanTasks() {
        return humanTasks;
    }

    public int serviceTasks() {
        return serviceTasks;
    }

    // =================================================================== graf modeli

    private static final class Node {
        final String id;
        final String tag;
        final String name;
        String attrs = "";
        String pre = "";
        String post = "";
        int w;
        int h;
        Edge defaultEdge;
        String direction;
        final List<Edge> in = new ArrayList<>();
        final List<Edge> out = new ArrayList<>();

        Node(String id, String tag, String name, int w, int h) {
            this.id = id;
            this.tag = tag;
            this.name = name;
            this.w = w;
            this.h = h;
        }
    }

    private static final class Edge {
        final String id;
        final Node from;
        Node to;
        String name;
        String cond;

        Edge(String id, Node from, Node to) {
            this.id = id;
            this.from = from;
            this.to = to;
        }
    }

    private static final class Seg {
        Node entry;
        Node exit;
        /** Cikis kenarinin adi / kosulu (insan gorevi kontrol kapisindan cikarken). */
        String exitName;
        String exitCond;
    }

    private final class Graph {
        final List<Node> nodes = new ArrayList<>();
        final List<Edge> edges = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        int flowSeq;

        Node add(String baseId, String tag, String name, int w, int h) {
            Node n = new Node(Names.unique(baseId, ids), tag, name, w, h);
            nodes.add(n);
            return n;
        }

        Edge edge(Node from, Node to, String name, String cond) {
            Edge e = new Edge("F" + (++flowSeq) + "_" + from.id + "_" + to.id, from, to);
            e.name = name;
            e.cond = cond;
            edges.add(e);
            from.out.add(e);
            to.in.add(e);
            return e;
        }

        Node gateway(String baseId, String name, boolean parallel, boolean diverging) {
            Node g = add(baseId, parallel ? "parallelGateway" : "exclusiveGateway", name, 50, 50);
            g.direction = diverging ? "Diverging" : "Converging";
            return g;
        }
    }

    // =================================================================== surec

    private String scope(Scope s, String processId, String displayName, String documentation, String origin) {
        bpmn.put(processId, ""); // sirayi korur: ana surec once, bloklar sonra
        Graph g = new Graph();
        Map<String, Seg> segs = new LinkedHashMap<>();
        Node endNode = null;

        for (Act a : s.acts) {
            Seg seg = new Seg();
            String base = Names.xmlId(a.name);
            if (a.isStart()) {
                Node n = g.add("Start", "startEvent", a.name, 36, 36);
                seg.entry = n;
                seg.exit = n;
            } else if (a.isEnd()) {
                Node n = g.add("End", "endEvent", a.name, 36, 36);
                seg.entry = n;
                seg.exit = n;
                endNode = n;
            } else if (a.isBlock()) {
                blockCall(g, seg, a, base, processId);
            } else if (a.isManual()) {
                humanTask(g, seg, a, base);
            } else {
                serviceTask(g, seg, a, base);
            }
            segs.put(a.index, seg);
        }

        // ---- baglantilar
        Map<String, List<Conn>> bySrc = new LinkedHashMap<>();
        Map<String, Integer> inDeg = new HashMap<>();
        for (Conn c : s.conns) {
            Act src = s.byIndex(c.src);
            Act dst = s.byIndex(c.dst);
            if (src == null || dst == null) {
                warnings.add("[" + displayName + "] Baglanti atlandi (aktivite bulunamadi): " + c.src + " -> " + c.dst);
                continue;
            }
            if (src.isEnd() || dst.isStart()) {
                warnings.add("[" + displayName + "] Gecersiz baglanti atlandi: " + src.name + " -> " + dst.name);
                continue;
            }
            bySrc.computeIfAbsent(c.src, k -> new ArrayList<>()).add(c);
            inDeg.merge(c.dst, 1, Integer::sum);
        }

        // AND birlesimi (StartCondition=A, >=2 giris): paralel birlesim kapisi
        Map<String, Node> entryOf = new HashMap<>();
        for (Act a : s.acts) {
            Seg seg = segs.get(a.index);
            Node entry = seg.entry;
            int deg = inDeg.getOrDefault(a.index, 0);
            if (deg >= 2 && !a.isEnd() && "A".equalsIgnoreCase(a.startCondition)) {
                Node join = g.gateway(Names.xmlId(a.name) + "_Birlesim", "Birlesim (AND)", true, false);
                g.edge(join, entry, null, null);
                entry = join;
                warnings.add("[" + displayName + "] '" + a.name + "' AND birlesim (StartCondition=A, " + deg
                        + " giris): gelen dallar paralel degilse surec burada bekler; gerekirse XOR'a cevirin.");
            }
            entryOf.put(a.index, entry);
        }

        for (Act a : s.acts) {
            Seg seg = segs.get(a.index);
            if (a.isEnd()) {
                continue;
            }
            List<Conn> outs = bySrc.getOrDefault(a.index, List.of());
            if (outs.isEmpty()) {
                if (endNode != null && seg.exit != endNode) {
                    g.edge(seg.exit, endNode, seg.exitName, seg.exitCond);
                }
                warnings.add("[" + displayName + "] '" + a.name + "' icin cikis baglantisi yok; End'e baglandi.");
                continue;
            }
            connectOutgoing(g, a, seg, outs, entryOf, displayName);
        }

        // ---- ulasilamayan dugumler
        Set<Node> reach = reachable(g);
        for (Act a : s.acts) {
            Node e = segs.get(a.index).entry;
            if (!a.isStart() && !reach.contains(e) && !reach.contains(entryOf.get(a.index))) {
                warnings.add("[" + displayName + "] '" + a.name + "' Start'tan ulasilamiyor (gelen baglanti yok).");
            }
        }

        mergeMultipleIncoming(g);
        String xml = render(g, processId, displayName, documentation, origin);
        bpmn.put(processId, xml);
        return processId;
    }

    /**
     * jBPM, gorev / call activity / end dugumlerinde birden fazla gelen akisa izin vermez. Birden cok
     * girisi olan her dugumun onune XOR birlestirme kapisi eklenir (Avrasya'daki StartCondition=V karsiligi).
     */
    private void mergeMultipleIncoming(Graph g) {
        for (Node n : new ArrayList<>(g.nodes)) {
            if (n.tag.endsWith("Gateway") || n.in.size() < 2) {
                continue;
            }
            Node merge = g.gateway(n.id + "_Giris", "", false, false);
            List<Edge> old = new ArrayList<>(n.in);
            n.in.clear();
            for (Edge e : old) {
                e.to = merge;
                merge.in.add(e);
            }
            g.edge(merge, n, null, null);
        }
    }

    private Set<Node> reachable(Graph g) {
        Set<Node> seen = new HashSet<>();
        Node start = null;
        for (Node n : g.nodes) {
            if ("startEvent".equals(n.tag)) {
                start = n;
                break;
            }
        }
        if (start == null) {
            return seen;
        }
        Deque<Node> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty()) {
            Node n = q.poll();
            for (Edge e : n.out) {
                if (seen.add(e.to)) {
                    q.add(e.to);
                }
            }
        }
        return seen;
    }

    private void connectOutgoing(Graph g, Act a, Seg seg, List<Conn> outs, Map<String, Node> entryOf, String where) {
        if (outs.size() == 1) {
            Conn c = outs.get(0);
            ConditionTranslator.Result r = ConditionTranslator.translate(c.condition, "c");
            if (!r.alwaysTrue) {
                warnings.add("[" + where + "] '" + a.name + "' tek cikis kosulu (" + c.condition
                        + ") akista uygulanmadi; kosul saglanmazsa Avrasya akisi durdurur. Gerekirse elle ekleyin.");
            }
            g.edge(seg.exit, entryOf.get(c.dst), seg.exitName != null ? seg.exitName : nz(c.description), seg.exitCond);
            return;
        }
        boolean allTrue = true;
        for (Conn c : outs) {
            allTrue &= ConditionTranslator.translate(c.condition, "c").alwaysTrue;
        }
        Node split = g.gateway(Names.xmlId(a.name) + "_Dal", allTrue ? "Paralel Dal" : "Kosul", allTrue, true);
        g.edge(seg.exit, split, seg.exitName, seg.exitCond);
        if (allTrue) {
            for (Conn c : outs) {
                g.edge(split, entryOf.get(c.dst), nz(c.description), null);
            }
            warnings.add("[" + where + "] '" + a.name + "' " + outs.size() + " kosulsuz (TRUE) cikisli: paralel (AND) dal olarak modellendi. "
                    + "Dallar aslinda birbirinin alternatifiyse kosullari ekleyip XOR'a cevirin.");
            return;
        }
        Edge def = null;
        List<Edge> es = new ArrayList<>();
        for (Conn c : outs) {
            ConditionTranslator.Result r = ConditionTranslator.translate(c.condition, "c");
            if (!r.ok) {
                warnings.add("[" + where + "] '" + a.name + "' kosulu cevrilemedi (" + r.problem + "): " + c.condition
                        + " -> TRUE sayildi, elle duzeltin.");
            }
            Edge e = g.edge(split, entryOf.get(c.dst), nz(c.description), r.alwaysTrue ? null
                    : "AvrasyaContainer c = Surec.container(kcontext);\nreturn " + r.java + ";");
            es.add(e);
            if (r.alwaysTrue && def == null) {
                def = e;
            }
        }
        if (def == null) {
            def = es.get(es.size() - 1);
            def.cond = null;
        }
        split.defaultEdge = def;
        warnings.add("[" + where + "] '" + a.name + "' cok cikislu kosullu: Avrasya dogru olan tum kosullari izler, "
                + "burada ilk dogru dal (XOR) izlenir; paralel istiyorsaniz kapıyı AND/OR yapin.");
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    // =================================================================== aktivite parcalari

    private void blockCall(Graph g, Seg seg, Act a, String base, String parentProcessId) {
        String childId = Names.unique(proc.name + "." + base.toUpperCase(), processIds);
        Node n = g.add(base, "callActivity", a.name, 140, 70);
        n.attrs = " drools:independent=\"false\" drools:waitForCompletion=\"true\" calledElement=\"" + xmlEscape(childId) + "\"";
        n.pre = docAndMeta(a, scriptFor(a, null));
        n.post = "<bpmn2:ioSpecification>\n"
                + "        <bpmn2:dataInput id=\"" + n.id + "_" + CONTAINER_VAR + "InputX\" drools:dtype=\"" + containerFqcn()
                + "\" itemSubjectRef=\"" + CONTAINER_TYPE_ITEM + "\" name=\"" + CONTAINER_VAR + "\"/>\n"
                + "        <bpmn2:dataOutput id=\"" + n.id + "_" + CONTAINER_VAR + "OutputX\" drools:dtype=\"" + containerFqcn()
                + "\" itemSubjectRef=\"" + CONTAINER_TYPE_ITEM + "\" name=\"" + CONTAINER_VAR + "\"/>\n"
                + "        <bpmn2:inputSet><bpmn2:dataInputRefs>" + n.id + "_" + CONTAINER_VAR + "InputX</bpmn2:dataInputRefs></bpmn2:inputSet>\n"
                + "        <bpmn2:outputSet><bpmn2:dataOutputRefs>" + n.id + "_" + CONTAINER_VAR + "OutputX</bpmn2:dataOutputRefs></bpmn2:outputSet>\n"
                + "      </bpmn2:ioSpecification>\n"
                + "      <bpmn2:dataInputAssociation><bpmn2:sourceRef>" + CONTAINER_VAR + "</bpmn2:sourceRef><bpmn2:targetRef>" + n.id
                + "_" + CONTAINER_VAR + "InputX</bpmn2:targetRef></bpmn2:dataInputAssociation>\n"
                + "      <bpmn2:dataOutputAssociation><bpmn2:sourceRef>" + n.id + "_" + CONTAINER_VAR
                + "OutputX</bpmn2:sourceRef><bpmn2:targetRef>" + CONTAINER_VAR + "</bpmn2:targetRef></bpmn2:dataOutputAssociation>";
        seg.entry = n;
        seg.exit = n;
        // alt surec: once uretilir (calledElement'in var olmasi icin)
        scope(a.block, childId, a.name, a.description,
                "Avrasya blok '" + a.name + "' (" + parentProcessId + " icinden cagrilir)");
    }

    private void serviceTask(Graph g, Seg seg, Act a, String base) {
        serviceTasks++;
        String pid = a.programId.isBlank() ? "" : a.programId;
        if (pid.isBlank()) {
            warnings.add("'" + a.name + "' otomatik aktivite ama ProgramID yok; servis cagrisi bos ProgramID ile uretildi.");
        } else {
            programIds.add(pid);
            programNames.merge(pid, a.name, (x, y) -> x.contains(y) ? x : x + ", " + y);
        }
        Node t = g.add(base, "task", a.name, 140, 70);
        t.attrs = " drools:taskName=\"AvrasyaService\"";
        t.pre = docAndMeta(a, scriptFor(a, null));
        t.post = ioSpecService(t.id, pid, a.name);
        Node chk = g.gateway(base + "_Kontrol", "Bitis Kosulu", false, true);
        Node err = g.add(base + "_Hata", "userTask", a.name + " - Hata Kuyrugu", 140, 70);
        errorTask(err, a, pid);
        Node dec = g.gateway(base + "_HataKarar", "Karar", false, true);
        Node out = g.gateway(base + "_Cikis", "", false, false);

        ConditionTranslator.Result end = ConditionTranslator.translate(a.endCondition, "c");
        if (!end.ok) {
            warnings.add("'" + a.name + "' EndCondition cevrilemedi (" + end.problem + "): " + a.endCondition
                    + " -> TRUE sayildi, elle duzeltin.");
        }
        g.edge(t, chk, null, null);
        g.edge(chk, out, "Basarili", "AvrasyaContainer c = Surec.container(kcontext);\n"
                + "return Surec.servisTamam((String) kcontext.getVariable(\"serviceError\"), " + end.java + ");");
        Edge bad = g.edge(chk, err, "Hata", null);
        chk.defaultEdge = bad;
        g.edge(err, dec, null, null);
        g.edge(dec, out, "Atla", "return Surec.hataAtla((String) kcontext.getVariable(\"uHataKarar\"));");
        Edge retry = g.edge(dec, t, "Tekrar Dene", null);
        dec.defaultEdge = retry;
        seg.entry = t;
        seg.exit = out;
    }

    private void humanTask(Graph g, Seg seg, Act a, String base) {
        humanTasks++;
        if (!a.programId.isBlank()) {
            programNames.putIfAbsent("(insan) " + a.programId, a.name);
        }
        Node t = g.add(base, "userTask", a.name, 140, 70);
        String group;
        String groupScript = null;
        switch (a.wqOption.toUpperCase()) {
            case "S":
                group = "WQ_" + a.wqId;
                break;
            case "C":
                group = null;
                groupScript = "kcontext.setVariable(\"uGrup\", Surec.grup(Surec.v(c, " + ConditionTranslator.javaString(a.wqFromContainer) + ")));";
                break;
            default:
                group = null;
                groupScript = "kcontext.setVariable(\"uGrup\", Surec.GRUP_VARSAYILAN);";
                warnings.add("'" + a.name + "' insan gorevinde kuyruk (WorkQueue) tanimsiz; varsayilan grup '" + opt.defaultGroup + "' kullanildi.");
        }
        t.pre = docAndMeta(a, scriptFor(a, groupScript));
        String id = t.id;
        StringBuilder io = new StringBuilder("<bpmn2:ioSpecification>\n");
        String[][] strs = {{"TaskName"}, {"NodeName"}, {"GroupId"}, {"Skippable"}, {"Comment"}, {"ProgramID"}};
        for (String[] s : strs) {
            io.append("        <bpmn2:dataInput id=\"").append(id).append('_').append(s[0]).append("InputX\" drools:dtype=\"String\" itemSubjectRef=\"_StringItem\" name=\"").append(s[0]).append("\"/>\n");
        }
        io.append("        <bpmn2:dataInput id=\"").append(id).append("_DataInputX\" drools:dtype=\"").append(containerFqcn())
                .append("\" itemSubjectRef=\"").append(CONTAINER_TYPE_ITEM).append("\" name=\"Data\"/>\n");
        io.append("        <bpmn2:dataOutput id=\"").append(id).append("_DataOutputX\" drools:dtype=\"").append(containerFqcn())
                .append("\" itemSubjectRef=\"").append(CONTAINER_TYPE_ITEM).append("\" name=\"Data\"/>\n");
        io.append("        <bpmn2:inputSet>");
        for (String[] s : strs) {
            io.append("<bpmn2:dataInputRefs>").append(id).append('_').append(s[0]).append("InputX</bpmn2:dataInputRefs>");
        }
        io.append("<bpmn2:dataInputRefs>").append(id).append("_DataInputX</bpmn2:dataInputRefs></bpmn2:inputSet>\n");
        io.append("        <bpmn2:outputSet><bpmn2:dataOutputRefs>").append(id).append("_DataOutputX</bpmn2:dataOutputRefs></bpmn2:outputSet>\n");
        io.append("      </bpmn2:ioSpecification>\n");
        io.append("      ").append(assignConst(id, "TaskName", a.name)).append('\n');
        io.append("      ").append(assignConst(id, "NodeName", a.name)).append('\n');
        io.append("      ").append(group != null ? assignConst(id, "GroupId", group) : assignVar(id, "GroupId", "uGrup")).append('\n');
        io.append("      ").append(assignConst(id, "Skippable", "false")).append('\n');
        io.append("      ").append(assignConst(id, "Comment", comment(a))).append('\n');
        io.append("      ").append(assignConst(id, "ProgramID", a.programId)).append('\n');
        io.append("      ").append(assignVar(id, "Data", CONTAINER_VAR)).append('\n');
        io.append("      <bpmn2:dataOutputAssociation><bpmn2:sourceRef>").append(id).append("_DataOutputX</bpmn2:sourceRef><bpmn2:targetRef>")
                .append(CONTAINER_VAR).append("</bpmn2:targetRef></bpmn2:dataOutputAssociation>");
        t.post = io.toString();

        ConditionTranslator.Result end = ConditionTranslator.translate(a.endCondition, "c");
        if (end.alwaysTrue) {
            seg.entry = t;
            seg.exit = t;
            return;
        }
        if (!end.ok) {
            warnings.add("'" + a.name + "' EndCondition cevrilemedi (" + end.problem + "): " + a.endCondition
                    + " -> TRUE sayildi, elle duzeltin.");
        }
        Node chk = g.gateway(base + "_Kontrol", "Bitis Kosulu", false, true);
        g.edge(t, chk, null, null);
        Edge again = g.edge(chk, t, "Tekrar (kosul saglanmadi)", null);
        chk.defaultEdge = again;
        seg.entry = t;
        seg.exit = chk;
        seg.exitName = "Tamam";
        seg.exitCond = "AvrasyaContainer c = Surec.container(kcontext);\nreturn " + end.java + ";";
    }

    private void errorTask(Node err, Act a, String pid) {
        String id = err.id;
        String group = "S".equalsIgnoreCase(a.wqOption) ? "WQ_" + a.wqId : opt.errorGroup;
        err.pre = "<bpmn2:extensionElements>\n"
                + "        <drools:metaData name=\"elementname\"><drools:metaValue>" + cdata(err.name) + "</drools:metaValue></drools:metaData>\n"
                + "        <drools:onEntry-script scriptFormat=\"http://www.java.com/java\"><drools:script>"
                + cdata("kcontext.setVariable(\"uHataKarar\", null);") + "</drools:script></drools:onEntry-script>\n"
                + "      </bpmn2:extensionElements>";
        String[] strs = {"TaskName", "NodeName", "GroupId", "Skippable", "Comment", "ProgramID", "ServiceError"};
        StringBuilder io = new StringBuilder("<bpmn2:ioSpecification>\n");
        for (String s : strs) {
            io.append("        <bpmn2:dataInput id=\"").append(id).append('_').append(s).append("InputX\" drools:dtype=\"String\" itemSubjectRef=\"_StringItem\" name=\"").append(s).append("\"/>\n");
        }
        io.append("        <bpmn2:dataInput id=\"").append(id).append("_DataInputX\" drools:dtype=\"").append(containerFqcn())
                .append("\" itemSubjectRef=\"").append(CONTAINER_TYPE_ITEM).append("\" name=\"Data\"/>\n");
        io.append("        <bpmn2:dataOutput id=\"").append(id).append("_kararOutputX\" drools:dtype=\"String\" itemSubjectRef=\"_StringItem\" name=\"karar\"/>\n");
        io.append("        <bpmn2:inputSet>");
        for (String s : strs) {
            io.append("<bpmn2:dataInputRefs>").append(id).append('_').append(s).append("InputX</bpmn2:dataInputRefs>");
        }
        io.append("<bpmn2:dataInputRefs>").append(id).append("_DataInputX</bpmn2:dataInputRefs></bpmn2:inputSet>\n");
        io.append("        <bpmn2:outputSet><bpmn2:dataOutputRefs>").append(id).append("_kararOutputX</bpmn2:dataOutputRefs></bpmn2:outputSet>\n");
        io.append("      </bpmn2:ioSpecification>\n");
        io.append("      ").append(assignConst(id, "TaskName", "HataKuyrugu")).append('\n');
        io.append("      ").append(assignConst(id, "NodeName", err.name)).append('\n');
        io.append("      ").append(assignConst(id, "GroupId", group)).append('\n');
        io.append("      ").append(assignConst(id, "Skippable", "false")).append('\n');
        io.append("      ").append(assignConst(id, "Comment", "Otomatik aktivite bitis kosulu saglanamadi: " + a.name
                + ". karar = TEKRAR (varsayilan) | ATLA")).append('\n');
        io.append("      ").append(assignConst(id, "ProgramID", pid)).append('\n');
        io.append("      ").append(assignVar(id, "ServiceError", "serviceError")).append('\n');
        io.append("      ").append(assignVar(id, "Data", CONTAINER_VAR)).append('\n');
        io.append("      <bpmn2:dataOutputAssociation><bpmn2:sourceRef>").append(id).append("_kararOutputX</bpmn2:sourceRef><bpmn2:targetRef>uHataKarar</bpmn2:targetRef></bpmn2:dataOutputAssociation>");
        err.post = io.toString();
    }

    private String ioSpecService(String id, String programId, String activityName) {
        StringBuilder io = new StringBuilder("<bpmn2:ioSpecification>\n");
        String[] strs = {"TaskName", "ProgramID", "ActivityName"};
        for (String s : strs) {
            io.append("        <bpmn2:dataInput id=\"").append(id).append('_').append(s).append("InputX\" drools:dtype=\"String\" itemSubjectRef=\"_StringItem\" name=\"").append(s).append("\"/>\n");
        }
        io.append("        <bpmn2:dataInput id=\"").append(id).append("_DataInputX\" drools:dtype=\"").append(containerFqcn())
                .append("\" itemSubjectRef=\"").append(CONTAINER_TYPE_ITEM).append("\" name=\"Data\"/>\n");
        io.append("        <bpmn2:dataOutput id=\"").append(id).append("_DataOutputX\" drools:dtype=\"").append(containerFqcn())
                .append("\" itemSubjectRef=\"").append(CONTAINER_TYPE_ITEM).append("\" name=\"Data\"/>\n");
        io.append("        <bpmn2:dataOutput id=\"").append(id).append("_ServiceErrorOutputX\" drools:dtype=\"String\" itemSubjectRef=\"_StringItem\" name=\"ServiceError\"/>\n");
        io.append("        <bpmn2:inputSet>");
        for (String s : strs) {
            io.append("<bpmn2:dataInputRefs>").append(id).append('_').append(s).append("InputX</bpmn2:dataInputRefs>");
        }
        io.append("<bpmn2:dataInputRefs>").append(id).append("_DataInputX</bpmn2:dataInputRefs></bpmn2:inputSet>\n");
        io.append("        <bpmn2:outputSet><bpmn2:dataOutputRefs>").append(id).append("_DataOutputX</bpmn2:dataOutputRefs><bpmn2:dataOutputRefs>")
                .append(id).append("_ServiceErrorOutputX</bpmn2:dataOutputRefs></bpmn2:outputSet>\n");
        io.append("      </bpmn2:ioSpecification>\n");
        io.append("      ").append(assignConst(id, "TaskName", "AvrasyaService")).append('\n');
        io.append("      ").append(assignConst(id, "ProgramID", programId)).append('\n');
        io.append("      ").append(assignConst(id, "ActivityName", activityName)).append('\n');
        io.append("      ").append(assignVar(id, "Data", CONTAINER_VAR)).append('\n');
        io.append("      <bpmn2:dataOutputAssociation><bpmn2:sourceRef>").append(id).append("_DataOutputX</bpmn2:sourceRef><bpmn2:targetRef>")
                .append(CONTAINER_VAR).append("</bpmn2:targetRef></bpmn2:dataOutputAssociation>\n");
        io.append("      <bpmn2:dataOutputAssociation><bpmn2:sourceRef>").append(id).append("_ServiceErrorOutputX</bpmn2:sourceRef><bpmn2:targetRef>serviceError</bpmn2:targetRef></bpmn2:dataOutputAssociation>");
        return io.toString();
    }

    private static String assignConst(String id, String name, String value) {
        return "<bpmn2:dataInputAssociation><bpmn2:targetRef>" + id + "_" + name + "InputX</bpmn2:targetRef><bpmn2:assignment>"
                + "<bpmn2:from xsi:type=\"bpmn2:tFormalExpression\">" + cdata(value) + "</bpmn2:from>"
                + "<bpmn2:to xsi:type=\"bpmn2:tFormalExpression\">" + cdata(id + "_" + name + "InputX") + "</bpmn2:to>"
                + "</bpmn2:assignment></bpmn2:dataInputAssociation>";
    }

    private static String assignVar(String id, String name, String variable) {
        return "<bpmn2:dataInputAssociation><bpmn2:sourceRef>" + variable + "</bpmn2:sourceRef><bpmn2:targetRef>" + id + "_" + name
                + "InputX</bpmn2:targetRef></bpmn2:dataInputAssociation>";
    }

    private String comment(Act a) {
        String d = a.documentation.isBlank() ? a.description : a.documentation;
        return d.isBlank() ? a.name : d.replaceAll("\\s+", " ").trim();
    }

    private String containerFqcn() {
        return pkg + ".model.AvrasyaContainer";
    }

    // =================================================================== esleme script'i

    /** NDM (girdi) eslemelerini Java script'ine cevirir; {@code extra} (grup hesabi) eklenir. */
    private String scriptFor(Act a, String extra) {
        StringBuilder sb = new StringBuilder();
        for (Mapping m : a.maps) {
            if (!m.isInput()) {
                continue;
            }
            String dst = m.dstNode.trim();
            if (dst.isEmpty() || dst.equals(containerRoot)) {
                continue;
            }
            switch (m.valueType.toUpperCase()) {
                case "S":
                    sb.append("Surec.set(c, ").append(ConditionTranslator.javaString(dst)).append(", ")
                            .append(ConditionTranslator.javaString(m.srcNode)).append(");\n");
                    break;
                case "C":
                    sb.append("Surec.set(c, ").append(ConditionTranslator.javaString(dst)).append(", ").append(concat(m.srcNode)).append(");\n");
                    break;
                default: // F
                    String src = m.srcNode.trim();
                    if (!src.isEmpty() && !src.equals(dst) && !src.equals(containerRoot) && src.contains("/")) {
                        sb.append("Surec.copy(c, ").append(ConditionTranslator.javaString(src)).append(", ")
                                .append(ConditionTranslator.javaString(dst)).append(");\n");
                    }
            }
        }
        if (extra != null) {
            sb.append(extra).append('\n');
        }
        if (sb.length() == 0) {
            return null;
        }
        return "AvrasyaContainer c = Surec.container(kcontext);\n" + sb + "kcontext.setVariable(\"" + CONTAINER_VAR + "\", c);";
    }

    /** "a/b+c/d" -> Surec.v(c,"a/b") + Surec.v(c,"c/d"); yol olmayan parca sabit metindir. */
    private static String concat(String expr) {
        List<String> parts = new ArrayList<>();
        for (String p : expr.split("\\+")) {
            String t = p.trim();
            parts.add(t.contains("/") ? "Surec.v(c, " + ConditionTranslator.javaString(t) + ")" : ConditionTranslator.javaString(t));
        }
        return parts.isEmpty() ? "\"\"" : String.join(" + ", parts);
    }

    private String docAndMeta(Act a, String script) {
        StringBuilder sb = new StringBuilder();
        String doc = (a.documentation.isBlank() ? a.description : a.documentation).replaceAll("\\s+", " ").trim();
        StringBuilder d = new StringBuilder(doc);
        if (!a.programId.isBlank()) {
            d.append(d.length() > 0 ? " | " : "").append("Avrasya ActivityIndex ").append(a.index).append(", ProgramID ").append(a.programId);
        }
        if (!"TRUE".equalsIgnoreCase(a.endCondition.trim())) {
            d.append(d.length() > 0 ? " | " : "").append("EndCondition: ").append(a.endCondition);
        }
        if (d.length() > 0) {
            sb.append("<bpmn2:documentation>").append(cdata(d.toString())).append("</bpmn2:documentation>\n      ");
        }
        sb.append("<bpmn2:extensionElements>\n        <drools:metaData name=\"elementname\"><drools:metaValue>")
                .append(cdata(a.name)).append("</drools:metaValue></drools:metaData>");
        if (script != null) {
            sb.append("\n        <drools:onEntry-script scriptFormat=\"http://www.java.com/java\"><drools:script>")
                    .append(cdata(script)).append("</drools:script></drools:onEntry-script>");
        }
        sb.append("\n      </bpmn2:extensionElements>");
        return sb.toString();
    }

    // =================================================================== render

    private String render(Graph g, String processId, String displayName, String documentation, String origin) {
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<bpmn2:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:bpmn2=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
                + "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" xmlns:bpsim=\"http://www.bpsim.org/schemas/1.0\" "
                + "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" "
                + "xmlns:drools=\"http://www.jboss.org/drools\" xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd "
                + "http://www.jboss.org/drools drools.xsd http://www.bpsim.org/schemas/1.0 bpsim.xsd http://www.omg.org/spec/DD/20100524/DC DC.xsd "
                + "http://www.omg.org/spec/DD/20100524/DI DI.xsd \" id=\"_" + Names.xmlId(processId) + "\" exporter=\"avrasya-bpm-converter\" "
                + "exporterVersion=\"1.0\" targetNamespace=\"http://www.omg.org/bpmn20\">\n");
        x.append("  <bpmn2:itemDefinition id=\"" + CONTAINER_TYPE_ITEM + "\" structureRef=\"").append(containerFqcn()).append("\"/>\n");
        x.append("  <bpmn2:itemDefinition id=\"_StringItem\" structureRef=\"String\"/>\n");
        x.append("  <bpmn2:process id=\"").append(xmlEscape(processId)).append("\" drools:packageName=\"").append(pkg)
                .append("\" drools:version=\"1.0\" drools:adHoc=\"false\" name=\"").append(xmlEscape(displayName))
                .append("\" isExecutable=\"true\" processType=\"Public\">\n");
        String doc = (documentation == null || documentation.isBlank() ? "" : documentation.trim() + " | ") + origin;
        x.append("    <bpmn2:documentation>").append(cdata(doc)).append("</bpmn2:documentation>\n");
        x.append("    <bpmn2:extensionElements>\n");
        x.append("      <drools:import name=\"").append(containerFqcn()).append("\"/>\n");
        x.append("      <drools:import name=\"").append(pkg).append(".surec.Surec\"/>\n");
        x.append("    </bpmn2:extensionElements>\n");
        x.append("    <bpmn2:property id=\"" + CONTAINER_VAR + "\" itemSubjectRef=\"" + CONTAINER_TYPE_ITEM + "\" name=\"" + CONTAINER_VAR + "\"/>\n");
        x.append("    <bpmn2:property id=\"serviceError\" itemSubjectRef=\"_StringItem\" name=\"serviceError\"/>\n");
        x.append("    <bpmn2:property id=\"uHataKarar\" itemSubjectRef=\"_StringItem\" name=\"uHataKarar\"/>\n");
        x.append("    <bpmn2:property id=\"uGrup\" itemSubjectRef=\"_StringItem\" name=\"uGrup\"/>\n");

        for (Node n : g.nodes) {
            x.append("    <bpmn2:").append(n.tag).append(" id=\"").append(n.id).append('"');
            if ("task".equals(n.tag) || "userTask".equals(n.tag) || "callActivity".equals(n.tag) || n.tag.endsWith("Event")
                    || n.tag.endsWith("Gateway")) {
                x.append(" name=\"").append(xmlEscape(n.name == null ? "" : n.name)).append('"');
            }
            x.append(n.attrs);
            if (n.direction != null) {
                x.append(" gatewayDirection=\"").append(n.direction).append('"');
            }
            if (n.defaultEdge != null) {
                x.append(" default=\"").append(n.defaultEdge.id).append('"');
            }
            x.append(">\n");
            if (!n.pre.isEmpty()) {
                x.append("      ").append(n.pre).append('\n');
            }
            for (Edge e : n.in) {
                x.append("      <bpmn2:incoming>").append(e.id).append("</bpmn2:incoming>\n");
            }
            for (Edge e : n.out) {
                x.append("      <bpmn2:outgoing>").append(e.id).append("</bpmn2:outgoing>\n");
            }
            if (!n.post.isEmpty()) {
                x.append("      ").append(n.post).append('\n');
            }
            x.append("    </bpmn2:").append(n.tag).append(">\n");
        }
        for (Edge e : g.edges) {
            x.append("    <bpmn2:sequenceFlow id=\"").append(e.id).append("\" sourceRef=\"").append(e.from.id)
                    .append("\" targetRef=\"").append(e.to.id).append('"');
            if (e.name != null) {
                x.append(" name=\"").append(xmlEscape(e.name)).append('"');
            }
            if (e.cond == null) {
                x.append("/>\n");
            } else {
                x.append(">\n      <bpmn2:extensionElements><drools:metaData name=\"elementname\"><drools:metaValue>")
                        .append(cdata(e.name == null ? "" : e.name)).append("</drools:metaValue></drools:metaData></bpmn2:extensionElements>\n");
                x.append("      <bpmn2:conditionExpression xsi:type=\"bpmn2:tFormalExpression\" language=\"http://www.java.com/java\">")
                        .append(cdata(e.cond)).append("</bpmn2:conditionExpression>\n");
                x.append("    </bpmn2:sequenceFlow>\n");
            }
        }
        x.append("  </bpmn2:process>\n");
        diagram(x, g, processId);
        String relId = "_" + Names.xmlId(processId);
        x.append("  <bpmn2:relationship type=\"BPSimData\"><bpmn2:extensionElements><bpsim:BPSimData><bpsim:Scenario id=\"default\" "
                + "name=\"Simulationscenario\"><bpsim:ScenarioParameters/></bpsim:Scenario></bpsim:BPSimData></bpmn2:extensionElements>"
                + "<bpmn2:source>").append(relId).append("</bpmn2:source><bpmn2:target>").append(relId)
                .append("</bpmn2:target></bpmn2:relationship>\n");
        x.append("</bpmn2:definitions>\n");
        return x.toString();
    }

    // =================================================================== diyagram (otomatik yerlesim)

    private void diagram(StringBuilder x, Graph g, String processId) {
        Map<Node, Integer> level = new HashMap<>();
        Map<Integer, Integer> rows = new HashMap<>();
        Map<Node, int[]> pos = new HashMap<>();
        Deque<Node> q = new ArrayDeque<>();
        List<Node> order = new ArrayList<>();
        for (Node n : g.nodes) {
            if ("startEvent".equals(n.tag)) {
                level.put(n, 0);
                q.add(n);
            }
        }
        while (true) {
            while (!q.isEmpty()) {
                Node n = q.poll();
                order.add(n);
                for (Edge e : n.out) {
                    if (!level.containsKey(e.to)) {
                        level.put(e.to, level.get(n) + 1);
                        q.add(e.to);
                    }
                }
            }
            Node rest = null;
            for (Node n : g.nodes) {
                if (!level.containsKey(n)) {
                    rest = n;
                    break;
                }
            }
            if (rest == null) {
                break;
            }
            int max = level.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            level.put(rest, max + 1);
            q.add(rest);
        }
        for (Node n : order) {
            int lv = level.get(n);
            int row = rows.merge(lv, 1, Integer::sum) - 1;
            int cx = 60 + lv * 200;
            int cy = 80 + row * 120;
            pos.put(n, new int[] {cx - n.w / 2, cy - n.h / 2});
        }
        x.append("  <bpmndi:BPMNDiagram id=\"diagram_").append(Names.xmlId(processId)).append("\">\n");
        x.append("    <bpmndi:BPMNPlane id=\"plane_").append(Names.xmlId(processId)).append("\" bpmnElement=\"")
                .append(xmlEscape(processId)).append("\">\n");
        for (Node n : g.nodes) {
            int[] p = pos.get(n);
            x.append("      <bpmndi:BPMNShape id=\"shape_").append(n.id).append("\" bpmnElement=\"").append(n.id)
                    .append("\"><dc:Bounds height=\"").append(n.h).append("\" width=\"").append(n.w).append("\" x=\"")
                    .append(p[0]).append("\" y=\"").append(p[1]).append("\"/></bpmndi:BPMNShape>\n");
        }
        for (Edge e : g.edges) {
            int[] a = pos.get(e.from);
            int[] b = pos.get(e.to);
            int ax = a[0] + e.from.w;
            int ay = a[1] + e.from.h / 2;
            int bx = b[0];
            int by = b[1] + e.to.h / 2;
            x.append("      <bpmndi:BPMNEdge id=\"edge_").append(e.id).append("\" bpmnElement=\"").append(e.id).append("\">");
            if (bx > ax) {
                x.append(wp(ax, ay)).append(wp(bx, by));
            } else { // geri donus: altindan dolas
                int ybot = Math.max(a[1] + e.from.h, b[1] + e.to.h) + 40;
                x.append(wp(a[0] + e.from.w / 2, a[1] + e.from.h)).append(wp(a[0] + e.from.w / 2, ybot))
                        .append(wp(b[0] + e.to.w / 2, ybot)).append(wp(b[0] + e.to.w / 2, b[1] + e.to.h));
            }
            x.append("</bpmndi:BPMNEdge>\n");
        }
        x.append("    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>\n");
    }

    private static String wp(int x, int y) {
        return "<di:waypoint x=\"" + x + "\" y=\"" + y + "\"/>";
    }
}
