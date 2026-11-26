package com.garantibbva.processarchitecture.converter;

import static com.garantibbva.processarchitecture.converter.Names.xmlEscape;

/** Uretilen Maven pom.xml dosyalari. */
final class PomWriter {
    private PomWriter() {
    }

    private static final String HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
            + "  <modelVersion>4.0.0</modelVersion>\n";

    static String aggregator(ConverterOptions o, String artifactId, String processName) {
        return HEAD
                + "\n  <!-- " + xmlEscape(processName) + ": Avrasya -> BAMOE donusumu (avrasya-bpm-converter ile uretildi) -->\n"
                + "  <groupId>" + o.groupId + "</groupId>\n"
                + "  <artifactId>" + artifactId + "</artifactId>\n"
                + "  <version>" + o.version + "</version>\n"
                + "  <packaging>pom</packaging>\n"
                + "  <name>" + xmlEscape(processName) + " :: Aggregator</name>\n\n"
                + "  <modules>\n"
                + "    <module>" + artifactId + "-kjar</module>\n"
                + "    <module>" + artifactId + "-service</module>\n"
                + "  </modules>\n\n"
                + "  <properties>\n"
                + "    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "  </properties>\n"
                + "</project>\n";
    }

    static String kjar(ConverterOptions o, String artifactId, String processName) {
        StringBuilder x = new StringBuilder(HEAD);
        if (o.useArkBpm) {
            x.append("\n  <!-- kjar, KIE'ye dogrudan degil ark-bpm kjar-parent'ina baglidir.\n"
                    + "       Parent koordinatlarini ark-bpm reposundaki kjar-parent/pom.xml'e gore dogrulayin. -->\n");
            x.append("  <parent>\n")
                    .append("    <groupId>").append(o.kjarParentGroupId).append("</groupId>\n")
                    .append("    <artifactId>").append(o.kjarParentArtifactId).append("</artifactId>\n")
                    .append("    <version>").append(o.kjarParentVersion).append("</version>\n")
                    .append("    <relativePath/>\n")
                    .append("  </parent>\n\n");
        }
        x.append("  <groupId>").append(o.groupId).append("</groupId>\n")
                .append("  <artifactId>").append(artifactId).append("-kjar</artifactId>\n")
                .append("  <version>").append(o.version).append("</version>\n")
                .append("  <packaging>kjar</packaging>\n")
                .append("  <name>").append(xmlEscape(processName)).append(" :: KJAR</name>\n\n");
        x.append("  <properties>\n")
                .append("    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n")
                .append("    <maven.compiler.release>").append(o.javaRelease).append("</maven.compiler.release>\n");
        if (!o.useArkBpm) {
            x.append("    <kie.version>").append(o.kieVersion).append("</kie.version>\n")
                    .append("    <mvel.version>2.5.2.Final</mvel.version>\n");
        }
        x.append("  </properties>\n\n  <dependencies>\n");
        for (String d : o.kjarDependencies.split(",")) {
            dep(x, o, d.trim(), null);
        }
        if (o.useArkBpm) {
            x.append("    <!-- Versiyonlari ark-bpm parent/BOM yonetir; yonetmiyorsa kjar-common'a tasiyin -->\n");
            x.append("    <dependency><groupId>org.kie</groupId><artifactId>kie-api</artifactId><scope>provided</scope></dependency>\n");
            x.append("    <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-annotations</artifactId><scope>provided</scope></dependency>\n");
            x.append("    <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><scope>provided</scope></dependency>\n");
            x.append("    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><scope>provided</scope></dependency>\n");
        } else {
            x.append("    <dependency><groupId>org.kie</groupId><artifactId>kie-api</artifactId><version>${kie.version}</version><scope>provided</scope></dependency>\n");
            x.append("    <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-annotations</artifactId><version>2.15.0</version><scope>provided</scope></dependency>\n");
            x.append("    <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>2.15.0</version><scope>provided</scope></dependency>\n");
            x.append("    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.36</version><scope>provided</scope></dependency>\n");
        }
        x.append("  </dependencies>\n\n  <build>\n    <plugins>\n      <plugin>\n")
                .append("        <groupId>org.kie</groupId>\n        <artifactId>kie-maven-plugin</artifactId>\n");
        if (!o.useArkBpm) {
            x.append("        <version>${kie.version}</version>\n");
        }
        x.append("        <extensions>true</extensions>\n");
        if (!o.useArkBpm) {
            x.append("        <dependencies>\n          <dependency><groupId>org.mvel</groupId><artifactId>mvel2</artifactId><version>${mvel.version}</version></dependency>\n        </dependencies>\n");
        }
        x.append("      </plugin>\n    </plugins>\n  </build>\n</project>\n");
        return x.toString();
    }

    static String service(ConverterOptions o, String artifactId, String processName) {
        StringBuilder x = new StringBuilder(HEAD);
        if (o.useArkBpm) {
            x.append("\n  <!-- service, KIE/Spring Boot'a dogrudan degil ark-bpm-starter-parent'a ve ark-bpm-springboot-* kutuphanelerine baglidir.\n"
                    + "       Koordinatlari ark-bpm reposundaki pom.xml dosyalarina gore dogrulayin. -->\n");
            x.append("  <parent>\n")
                    .append("    <groupId>").append(o.serviceParentGroupId).append("</groupId>\n")
                    .append("    <artifactId>").append(o.serviceParentArtifactId).append("</artifactId>\n")
                    .append("    <version>").append(o.serviceParentVersion).append("</version>\n")
                    .append("    <relativePath/>\n")
                    .append("  </parent>\n\n");
        }
        x.append("  <groupId>").append(o.groupId).append("</groupId>\n")
                .append("  <artifactId>").append(artifactId).append("-service</artifactId>\n")
                .append("  <version>").append(o.version).append("</version>\n")
                .append("  <name>").append(xmlEscape(processName)).append(" :: Service</name>\n\n");
        x.append("  <properties>\n")
                .append("    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n")
                .append("    <maven.compiler.release>").append(o.javaRelease).append("</maven.compiler.release>\n");
        if (!o.useArkBpm) {
            x.append("    <kie.version>").append(o.kieVersion).append("</kie.version>\n")
                    .append("    <spring-boot.version>2.5.15</spring-boot.version>\n")
                    .append("    <mvel.version>2.5.2.Final</mvel.version>\n")
                    .append("    <jackson.version>2.15.0</jackson.version>\n");
        }
        x.append("  </properties>\n\n");
        if (!o.useArkBpm) {
            x.append("  <dependencyManagement>\n    <dependencies>\n"
                    + "      <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>${spring-boot.version}</version><type>pom</type><scope>import</scope></dependency>\n"
                    + "      <dependency><groupId>com.fasterxml.jackson</groupId><artifactId>jackson-bom</artifactId><version>${jackson.version}</version><type>pom</type><scope>import</scope></dependency>\n"
                    + "      <dependency><groupId>org.mvel</groupId><artifactId>mvel2</artifactId><version>${mvel.version}</version></dependency>\n"
                    + "      <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><version>1.4.197</version></dependency>\n"
                    + "      <!-- KIE 7 community POM'lari bu artefaktlarin yalnizca JBoss reposundaki .redhat surumlerini ister; Central esdegerleri -->\n"
                    + "      <dependency><groupId>org.codehaus.jackson</groupId><artifactId>jackson-core-asl</artifactId><version>1.9.13</version></dependency>\n"
                    + "      <dependency><groupId>org.codehaus.jackson</groupId><artifactId>jackson-mapper-asl</artifactId><version>1.9.13</version></dependency>\n"
                    + "      <dependency><groupId>org.codehaus.jackson</groupId><artifactId>jackson-jaxrs</artifactId><version>1.9.13</version></dependency>\n"
                    + "      <dependency><groupId>org.codehaus.jackson</groupId><artifactId>jackson-xc</artifactId><version>1.9.13</version></dependency>\n"
                    + "      <dependency><groupId>xmlpull</groupId><artifactId>xmlpull</artifactId><version>1.1.3.1</version></dependency>\n"
                    + "      <dependency><groupId>xerces</groupId><artifactId>xercesImpl</artifactId><version>2.12.2</version></dependency>\n"
                    + "    </dependencies>\n  </dependencyManagement>\n\n");
        }
        x.append("  <dependencies>\n");
        x.append("    <dependency>\n      <groupId>").append(o.groupId).append("</groupId>\n      <artifactId>").append(artifactId)
                .append("-kjar</artifactId>\n      <version>").append(o.version).append("</version>\n    </dependency>\n");
        if (o.useArkBpm) {
            for (String d : o.serviceDependencies.split(",")) {
                dep(x, o, d.trim(), null);
            }
        } else {
            x.append("    <dependency><groupId>org.kie</groupId><artifactId>kie-server-spring-boot-starter-jbpm</artifactId><version>${kie.version}</version></dependency>\n");
            x.append("    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>\n");
            x.append("    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>\n");
            x.append("    <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>\n");
        }
        x.append("  </dependencies>\n");
        if (!o.useArkBpm) {
            x.append("\n  <build>\n    <plugins>\n      <plugin>\n        <groupId>org.springframework.boot</groupId>\n"
                    + "        <artifactId>spring-boot-maven-plugin</artifactId>\n        <version>${spring-boot.version}</version>\n"
                    + "      </plugin>\n    </plugins>\n  </build>\n");
        }
        x.append("</project>\n");
        return x.toString();
    }

    /** "artifactId" ya da "groupId:artifactId[:version]" */
    private static void dep(StringBuilder x, ConverterOptions o, String spec, String scope) {
        if (spec.isEmpty()) {
            return;
        }
        String[] p = spec.split(":");
        String g = p.length >= 2 ? p[0] : o.groupId;
        String a = p.length >= 2 ? p[1] : p[0];
        x.append("    <dependency>\n      <groupId>").append(g).append("</groupId>\n      <artifactId>").append(a).append("</artifactId>\n");
        if (p.length >= 3) {
            x.append("      <version>").append(p[2]).append("</version>\n");
        }
        if (scope != null) {
            x.append("      <scope>").append(scope).append("</scope>\n");
        }
        x.append("    </dependency>\n");
    }
}
