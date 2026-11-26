package com.garantibbva.processarchitecture.converter;

import com.garantibbva.processarchitecture.converter.gen.BpmnGenerator;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Act;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Mapping;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Scope;
import com.garantibbva.processarchitecture.converter.parse.AvrasyaParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Giris noktasi: Avrasya (Oracle Workflow) XML'i -> BAMOE 8 / jBPM 7 Maven projesi (kjar + service).
 *
 * <pre>
 * ConversionResult r = new Converter(options).convert(Files.readString(xmlPath));
 * r.writeTo(outDir);
 * </pre>
 */
public final class Converter {

    private final ConverterOptions opt;

    public Converter(ConverterOptions options) {
        this.opt = options.copy();
    }

    public ConversionResult convert(Path xmlFile) throws IOException {
        return convert(new String(Files.readAllBytes(xmlFile), StandardCharsets.UTF_8));
    }

    public ConversionResult convert(String xml) {
        AvrasyaParser parser = new AvrasyaParser();
        AvrasyaProcess p = parser.parse(xml);

        String artifactId = opt.artifactId.isBlank() ? Names.kebab(p.name) : opt.artifactId.trim();
        String pkg = opt.basePackage.isBlank() ? opt.groupId + "." + Names.packagePart(p.name) : opt.basePackage.trim();
        String root = detectContainerRoot(p);
        ConversionResult res = new ConversionResult(p.name, artifactId);
        res.warnings.addAll(parser.warnings());

        BpmnGenerator gen = new BpmnGenerator(opt, p, pkg, root, res.warnings);
        Map<String, String> processes = gen.generate();

        String pkgPath = pkg.replace('.', '/');
        String kjar = artifactId + "-kjar/";
        String svc = artifactId + "-service/";
        String className = Names.pascal(p.name);
        String slug = Names.kebab(p.name);

        Map<String, String> v = new HashMap<>();
        v.put("pkg", pkg);
        v.put("artifactId", artifactId);
        v.put("processName", p.name);
        v.put("processId", p.name);
        v.put("containerRoot", root);
        v.put("defaultGroup", opt.defaultGroup);
        v.put("errorGroup", opt.errorGroup);
        v.put("className", className);
        v.put("slug", slug);
        v.put("kbase", Names.xmlId(className.substring(0, 1).toLowerCase() + className.substring(1)) + "KBase");
        v.put("ksession", Names.xmlId(className.substring(0, 1).toLowerCase() + className.substring(1)) + "KSession");
        v.put("programIds", gen.programIds().isEmpty() ? "-" : String.join(", ", gen.programIds()));
        List<String> keyPaths = keyPaths(p, root);
        v.put("keyPaths", keyPaths.isEmpty() ? "" : "\"" + String.join("\", \"", keyPaths) + "\"");
        v.put("keyDoc", keyPaths.isEmpty()
                ? "ProcessInfo'da surec anahtari (Attributes/RefNode) yok; anahtar rastgele uretilir, durum {@code pid-<id>} ile sorgulanir."
                : "anahtar = " + String.join(" : ", keyPaths));
        v.put("programProps", programProps(gen));

        // ---- BPMN'ler
        for (Map.Entry<String, String> e : processes.entrySet()) {
            res.files.put(kjar + "src/main/resources/" + pkgPath + "/" + e.getKey() + ".bpmn", e.getValue());
        }
        res.files.put(kjar + "src/main/resources/" + pkgPath + "/AvrasyaService.wid", Templates.render("AvrasyaService.wid.tpl", v));
        res.files.put(kjar + "src/main/resources/META-INF/kmodule.xml", Templates.render("kmodule.xml.tpl", v));
        res.files.put(kjar + "src/main/resources/META-INF/kie-deployment-descriptor.xml",
                Templates.render("kie-deployment-descriptor.xml.tpl", v));
        // ---- kjar Java
        res.files.put(kjar + "src/main/java/" + pkgPath + "/model/AvrasyaContainer.java", Templates.render("AvrasyaContainer.java.tpl", v));
        res.files.put(kjar + "src/main/java/" + pkgPath + "/surec/Surec.java", Templates.render("Surec.java.tpl", v));
        res.files.put(kjar + "src/main/java/" + pkgPath + "/handler/AvrasyaServiceWorkItemHandler.java",
                Templates.render("AvrasyaServiceWorkItemHandler.java.tpl", v));
        res.files.put(kjar + "src/main/java/" + pkgPath + "/handler/AvrasyaServiceConfig.java",
                Templates.render("AvrasyaServiceConfig.java.tpl", v));
        // ---- service
        res.files.put(svc + "src/main/java/" + pkgPath + "/service/" + className + "Application.java", Templates.render("Application.java.tpl", v));
        res.files.put(svc + "src/main/java/" + pkgPath + "/service/AvrasyaServiceEnvironmentPostProcessor.java",
                Templates.render("AvrasyaServiceEnvironmentPostProcessor.java.tpl", v));
        res.files.put(svc + "src/main/java/" + pkgPath + "/service/SurecEntegrasyonController.java",
                Templates.render("SurecEntegrasyonController.java.tpl", v));
        res.files.put(svc + "src/main/java/" + pkgPath + "/service/AvrasyaGeriDonusController.java",
                Templates.render("AvrasyaGeriDonusController.java.tpl", v));
        res.files.put(svc + "src/main/resources/application.properties", Templates.render("application.properties.tpl", v));
        res.files.put(svc + "src/main/resources/META-INF/spring.factories", Templates.render("spring.factories.tpl", v));
        // ---- pom'lar
        res.files.put("pom.xml", PomWriter.aggregator(opt, artifactId, p.name));
        res.files.put(kjar + "pom.xml", PomWriter.kjar(opt, artifactId, p.name));
        res.files.put(svc + "pom.xml", PomWriter.service(opt, artifactId, p.name));
        res.files.put(".gitignore", "target/\n*.iml\n.idea/\n");

        res.info.add(p.name + ": " + processes.size() + " BPMN, " + gen.serviceTasks() + " otomatik (servis) aktivite, "
                + gen.humanTasks() + " insan gorevi, " + gen.programIds().size() + " ProgramID");
        res.files.put("CONVERSION_REPORT.md", Report.build(p, opt, res, gen, processes, root, keyPaths));
        return res;
    }

    // ------------------------------------------------------------------ yardimcilar

    private static final Pattern FIRST_SEGMENT = Pattern.compile("(?<![A-Za-z0-9_/])([A-Za-z][A-Za-z0-9_]*)/[A-Za-z0-9_]");

    /** Konteyner kok adi (orn. sExport_sALL): yollarda en cok gecen ilk parca. */
    static String detectContainerRoot(AvrasyaProcess p) {
        Map<String, Integer> count = new LinkedHashMap<>();
        List<String> texts = new ArrayList<>(p.attributes.values());
        collect(p.root, texts);
        for (String t : texts) {
            Matcher m = FIRST_SEGMENT.matcher(t);
            while (m.find()) {
                count.merge(m.group(1), 1, Integer::sum);
            }
        }
        String best = "";
        int bestN = 0;
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            boolean preferred = e.getKey().endsWith("_sALL") || e.getKey().endsWith("sALL");
            int n = e.getValue() + (preferred ? 1_000_000 : 0);
            if (n > bestN) {
                best = e.getKey();
                bestN = n;
            }
        }
        return best;
    }

    private static void collect(Scope s, List<String> out) {
        for (Act a : s.acts) {
            out.add(a.endCondition);
            out.add(a.wqFromContainer);
            for (Mapping m : a.maps) {
                out.add(m.srcNode);
                out.add(m.dstNode);
            }
            if (a.block != null) {
                collect(a.block, out);
            }
        }
        for (AvrasyaProcess.Conn c : s.conns) {
            out.add(c.condition);
        }
    }

    private static List<String> keyPaths(AvrasyaProcess p, String root) {
        List<String> res = new ArrayList<>();
        for (String k : new String[] {"RefBranchNum", "RefCustomerNum", "RefTrxType", "RefSequenceNum"}) {
            String ref = p.attributes.get(k);
            if (ref != null && !ref.isBlank()) {
                String r = ref.trim();
                if (!root.isEmpty() && r.startsWith(root + "/")) {
                    r = r.substring(root.length() + 1);
                }
                res.add(r.replace("\\", "\\\\").replace("\"", "\\\""));
            }
        }
        return res;
    }

    private static String programProps(BpmnGenerator gen) {
        StringBuilder sb = new StringBuilder();
        for (String id : gen.programIds()) {
            sb.append("# ").append(gen.programNames().getOrDefault(id, "")).append('\n');
            sb.append("#avrasya.service.program.").append(id).append(".url=http://servis-adresi/").append(id).append('\n');
            sb.append("#avrasya.service.program.").append(id).append(".mode=sync\n");
        }
        return sb.toString();
    }

    /** Siniftan yuklenen kaynak dosyasi okuyucu. */
    static final class Templates {
        private Templates() {
        }

        static String render(String name, Map<String, String> vars) {
            String t = read("/templates/" + name);
            for (Map.Entry<String, String> e : vars.entrySet()) {
                t = t.replace("@@" + e.getKey() + "@@", e.getValue());
            }
            if (t.contains("@@")) {
                int i = t.indexOf("@@");
                throw new IllegalStateException("Sablonda cozulmemis yer tutucu: " + name + " -> "
                        + t.substring(i, Math.min(t.length(), i + 30)).replace('\n', ' '));
            }
            return t;
        }

        private static String read(String path) {
            try (InputStream in = Converter.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("Sablon bulunamadi: " + path);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
