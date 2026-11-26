package com.garantibbva.processarchitecture.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Komut satiri: {@code java -jar converter-core.jar <avrasya.xml> <cikti-klasoru> [--no-ark] [--group g] [--artifact a]
 * [--package p] [--version v] [--config dosya.properties]}
 */
public final class Cli {
    private Cli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Kullanim: <avrasya.xml> <cikti-klasoru> [--config f.properties] [--group g] [--artifact a]"
                    + " [--package p] [--version v] [--no-ark]");
            System.exit(2);
        }
        Path cfg = null;
        for (int i = 2; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) {
                cfg = Paths.get(args[i + 1]);
            }
        }
        ConverterOptions o = ConverterOptions.load(cfg);
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--group": o.groupId = args[++i]; break;
                case "--artifact": o.artifactId = args[++i]; break;
                case "--package": o.basePackage = args[++i]; break;
                case "--version": o.version = args[++i]; break;
                case "--no-ark": o.useArkBpm = false; break;
                case "--config": i++; break;
                default: System.err.println("Bilinmeyen secenek: " + args[i]); System.exit(2);
            }
        }
        ConversionResult r = new Converter(o).convert(Files.readString(Paths.get(args[0])));
        Path out = r.writeTo(Paths.get(args[1]));
        r.info.forEach(System.out::println);
        System.out.println("Uyari sayisi: " + r.warnings.size());
        r.warnings.forEach(w -> System.out.println("  ! " + w));
        System.out.println("Yazildi: " + out.toAbsolutePath());
    }
}
