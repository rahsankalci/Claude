package com.garantibbva.processarchitecture.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Uretilecek projenin ayarlari. Alanlar bos birakilirsa surec adindan turetilir.
 *
 * <p><b>ark-bpm baglantisi.</b> Uretilen kjar ve service pom'lari {@code org.kie} / jBPM'e dogrudan
 * degil, ark-bpm deposundaki parent / BOM'a baglanir ({@link #kjarParent*}, {@link #serviceParent*}).
 * Gercek artifact koordinatlarini ark-bpm'in pom.xml dosyalarindan teyit edip burada verin.
 */
public final class ConverterOptions {

    // ---- uretilen proje kimligi
    public String groupId = "com.garantibbva.processarchitecture";
    /** Bos ise surec adindan (kebab-case). */
    public String artifactId = "";
    public String version = "1.0.0-SNAPSHOT";
    /** Bos ise {@code <groupId>.<surecadi-kucuk-harf>}. */
    public String basePackage = "";

    // ---- ark-bpm baglantisi
    /** true: pom'lar ark-bpm parent'larina baglanir. false: dogrudan KIE (yalnizca deneme / dogrulama icin). */
    public boolean useArkBpm = true;
    /** kjar modulunun parent'i (ark-bpm deposundaki kjar-parent). */
    public String kjarParentGroupId = "com.garantibbva.processarchitecture";
    public String kjarParentArtifactId = "kjar-parent";
    public String kjarParentVersion = "1.0.0-SNAPSHOT";
    /** service modulunun parent'i (ark-bpm-starter-parent). */
    public String serviceParentGroupId = "com.garantibbva.processarchitecture";
    public String serviceParentArtifactId = "ark-bpm-starter-parent";
    public String serviceParentVersion = "1.0.0-SNAPSHOT";
    /** service modulune eklenecek ark-bpm kutuphaneleri: "artifactId" ya da "groupId:artifactId" (virgulle). */
    public String serviceDependencies = "ark-bpm-springboot-app";
    /** kjar'a eklenecek ark-bpm kutuphaneleri (orn. kjar-common). Bos olabilir. */
    public String kjarDependencies = "";

    // ---- dogrudan KIE modu (useArkBpm=false)
    public String kieVersion = "7.74.1.Final";

    // ---- calisma zamani
    public String defaultGroup = "WQ_GENEL";
    public String errorGroup = "WQ_HATA";
    public String javaRelease = "17";

    /** Ayarlari ~/.avrasya-bpm-converter.properties gibi bir dosyaya yazar/okur (masaustu & IntelliJ). */
    public void save(OutputStream out) throws IOException {
        Properties p = new Properties();
        p.setProperty("groupId", groupId);
        p.setProperty("artifactId", artifactId);
        p.setProperty("version", version);
        p.setProperty("basePackage", basePackage);
        p.setProperty("useArkBpm", String.valueOf(useArkBpm));
        p.setProperty("kjarParentGroupId", kjarParentGroupId);
        p.setProperty("kjarParentArtifactId", kjarParentArtifactId);
        p.setProperty("kjarParentVersion", kjarParentVersion);
        p.setProperty("serviceParentGroupId", serviceParentGroupId);
        p.setProperty("serviceParentArtifactId", serviceParentArtifactId);
        p.setProperty("serviceParentVersion", serviceParentVersion);
        p.setProperty("serviceDependencies", serviceDependencies);
        p.setProperty("kjarDependencies", kjarDependencies);
        p.setProperty("kieVersion", kieVersion);
        p.setProperty("defaultGroup", defaultGroup);
        p.setProperty("errorGroup", errorGroup);
        p.setProperty("javaRelease", javaRelease);
        p.store(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8), "Avrasya -> BPM converter");
    }

    public static ConverterOptions load(Path file) {
        ConverterOptions o = new ConverterOptions();
        if (file == null || !Files.isRegularFile(file)) {
            return o;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file);
             java.io.Reader r = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return o;
        }
        o.groupId = p.getProperty("groupId", o.groupId);
        o.artifactId = p.getProperty("artifactId", o.artifactId);
        o.version = p.getProperty("version", o.version);
        o.basePackage = p.getProperty("basePackage", o.basePackage);
        o.useArkBpm = Boolean.parseBoolean(p.getProperty("useArkBpm", String.valueOf(o.useArkBpm)));
        o.kjarParentGroupId = p.getProperty("kjarParentGroupId", o.kjarParentGroupId);
        o.kjarParentArtifactId = p.getProperty("kjarParentArtifactId", o.kjarParentArtifactId);
        o.kjarParentVersion = p.getProperty("kjarParentVersion", o.kjarParentVersion);
        o.serviceParentGroupId = p.getProperty("serviceParentGroupId", o.serviceParentGroupId);
        o.serviceParentArtifactId = p.getProperty("serviceParentArtifactId", o.serviceParentArtifactId);
        o.serviceParentVersion = p.getProperty("serviceParentVersion", o.serviceParentVersion);
        o.serviceDependencies = p.getProperty("serviceDependencies", o.serviceDependencies);
        o.kjarDependencies = p.getProperty("kjarDependencies", o.kjarDependencies);
        o.kieVersion = p.getProperty("kieVersion", o.kieVersion);
        o.defaultGroup = p.getProperty("defaultGroup", o.defaultGroup);
        o.errorGroup = p.getProperty("errorGroup", o.errorGroup);
        o.javaRelease = p.getProperty("javaRelease", o.javaRelease);
        return o;
    }

    public ConverterOptions copy() {
        ConverterOptions o = new ConverterOptions();
        o.groupId = groupId; o.artifactId = artifactId; o.version = version; o.basePackage = basePackage;
        o.useArkBpm = useArkBpm;
        o.kjarParentGroupId = kjarParentGroupId; o.kjarParentArtifactId = kjarParentArtifactId; o.kjarParentVersion = kjarParentVersion;
        o.serviceParentGroupId = serviceParentGroupId; o.serviceParentArtifactId = serviceParentArtifactId; o.serviceParentVersion = serviceParentVersion;
        o.serviceDependencies = serviceDependencies; o.kjarDependencies = kjarDependencies;
        o.kieVersion = kieVersion; o.defaultGroup = defaultGroup; o.errorGroup = errorGroup; o.javaRelease = javaRelease;
        return o;
    }
}
