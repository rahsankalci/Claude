package com.garantibbva.processarchitecture.converter;

import com.garantibbva.processarchitecture.converter.gen.BpmnGenerator;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Act;
import com.garantibbva.processarchitecture.converter.model.AvrasyaProcess.Scope;
import java.util.List;
import java.util.Map;

/** CONVERSION_REPORT.md: ne uretildi, aktivite eslemesi, uyarilar, siradaki adimlar. */
final class Report {
    private Report() {
    }

    static String build(AvrasyaProcess p, ConverterOptions o, ConversionResult res, BpmnGenerator gen,
                        Map<String, String> processes, String root, List<String> keyPaths) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(p.name).append(" - Avrasya -> BAMOE donusum raporu\n\n");
        md.append("Kaynak: ACLID ").append(p.aclId).append(", CategoryID ").append(p.categoryId)
                .append(", konteyner ").append(p.containerId).append(" (`").append(root.isEmpty() ? "?" : root).append("`)\n\n");
        if (!p.description.isBlank()) {
            md.append("> ").append(p.description.replaceAll("\\s+", " ")).append("\n\n");
        }
        md.append("## Ozet\n\n");
        for (String i : res.info) {
            md.append("- ").append(i).append('\n');
        }
        md.append("- Maven: `").append(o.groupId).append(':').append(res.artifactId).append(":").append(o.version).append("` (kjar + service)\n");
        md.append("- ark-bpm baglantisi: ").append(o.useArkBpm
                ? "kjar parent `" + o.kjarParentGroupId + ":" + o.kjarParentArtifactId + ":" + o.kjarParentVersion
                + "`, service parent `" + o.serviceParentGroupId + ":" + o.serviceParentArtifactId + ":" + o.serviceParentVersion + "`"
                : "KAPALI (dogrudan KIE " + o.kieVersion + " - yalnizca deneme icin)").append('\n');
        md.append("- Surec anahtari (correlation key): ").append(keyPaths.isEmpty() ? "yok" : String.join(" : ", keyPaths)).append("\n\n");

        md.append("## BPMN surecleri\n\n");
        for (String id : processes.keySet()) {
            md.append("- `").append(id).append(".bpmn`\n");
        }
        md.append("\n## Aktivite eslemesi\n\n");
        md.append("| Kapsam | Aktivite | Tur | ProgramID | Kuyruk | Bitis kosulu |\n|---|---|---|---|---|---|\n");
        table(md, p.root, p.name);

        md.append("\n## Uyarilar (").append(res.warnings.size()).append(")\n\n");
        if (res.warnings.isEmpty()) {
            md.append("Yok.\n");
        }
        for (String w : res.warnings) {
            md.append("- ").append(w).append('\n');
        }
        md.append("\n## Siradaki adimlar\n\n");
        md.append("1. `").append(res.artifactId).append("-kjar/pom.xml` ve `").append(res.artifactId)
                .append("-service/pom.xml` icindeki ark-bpm parent / bagimlilik koordinatlarini ark-bpm reposundaki gercek pom.xml'lerle dogrulayin.\n");
        md.append("2. `").append(res.artifactId).append("-service/src/main/resources/application.properties` icinde ProgramID servis adreslerini girin; ")
                .append("mainframe'e gidip geri donen programlar icin `.mode=async` yapin.\n");
        md.append("3. Uyarilari gozden gecirin (ozellikle cok cikisli kosullar ve cevrilemeyen kosullar).\n");
        md.append("4. `mvn clean install` ile kjar'i derleyin; BPMN'leri Business Central / VS Code BPMN editorunde acip akisi dogrulayin.\n\n");
        md.append("## Donusum kurallari\n\n");
        md.append("- `StartOption=A` -> `AvrasyaService` servis gorevi; `EndCondition` saglanmaz ya da teknik hata olursa **Hata Kuyrugu** insan gorevi (karar: TEKRAR / ATLA).\n");
        md.append("- `StartOption=M` -> insan gorevi; `WorkQueueOption=S` -> grup `WQ_<id>`, `C` -> konteynerdeki degerden grup. `EndCondition` saglanmazsa gorev yeniden acilir.\n");
        md.append("- `Block` -> ayri BPMN + call activity (konteyner `container` degiskeniyle gider/gelir).\n");
        md.append("- Baglanti: tek cikis duz akis; cok cikisin hepsi `TRUE` ise paralel (AND), degilse kosullu (XOR, ilk dogru dal).\n");
        md.append("- `NDM` eslemeleri (F/S/C) aktivitenin giris script'ine; `DDC/DLC` cikti eslemeleri tek konteyner modelinde gereksizdir.\n");
        md.append("- Konteyner: yol tabanli `AvrasyaContainer` (JSON = konteynerin kendisi), bilinmeyen dugumler kaybolmaz.\n");
        return md.toString();
    }

    private static void table(StringBuilder md, Scope s, String where) {
        for (Act a : s.acts) {
            if (a.isStart() || a.isEnd()) {
                continue;
            }
            String kind = a.isBlock() ? "Blok" : a.isManual() ? "Insan gorevi" : "Servis";
            String queue = "S".equalsIgnoreCase(a.wqOption) ? "WQ_" + a.wqId
                    : "C".equalsIgnoreCase(a.wqOption) ? "<" + a.wqFromContainer + ">" : "-";
            md.append("| ").append(esc(where)).append(" | ").append(esc(a.name)).append(" | ").append(kind).append(" | ")
                    .append(a.programId.isBlank() ? "-" : a.programId).append(" | ").append(esc(queue)).append(" | `")
                    .append(esc(a.endCondition)).append("` |\n");
            if (a.block != null) {
                table(md, a.block, a.name);
            }
        }
    }

    private static String esc(String s) {
        return s.replace("|", "\\|").replace("\n", " ");
    }
}
