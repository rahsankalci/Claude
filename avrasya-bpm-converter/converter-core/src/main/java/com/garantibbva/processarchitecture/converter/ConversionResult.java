package com.garantibbva.processarchitecture.converter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Donusum ciktisi: goreli yol -> dosya icerigi, uyarilar ve ozet. */
public final class ConversionResult {
    public final String processName;
    public final String artifactId;
    public final Map<String, String> files = new LinkedHashMap<>();
    public final List<String> warnings = new ArrayList<>();
    public final List<String> info = new ArrayList<>();

    ConversionResult(String processName, String artifactId) {
        this.processName = processName;
        this.artifactId = artifactId;
    }

    /** Dosyalari {@code outDir/<artifactId>/...} altina yazar; proje klasorunu dondurur. */
    public Path writeTo(Path outDir) throws IOException {
        Path root = outDir.resolve(artifactId);
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path p = root.resolve(e.getKey()).normalize();
            if (!p.startsWith(root)) {
                throw new IOException("Gecersiz yol: " + e.getKey());
            }
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }
}
