// IntelliJ eklentisi: converter-core kaynaklarini dogrudan derler (ayri jar / Maven gerekmez).
// Derleme:   ./gradlew buildPlugin      -> build/distributions/*.zip
// Deneme:    ./gradlew runIde
// IDE'ye yukleme: Settings > Plugins > ⚙ > Install Plugin from Disk... > zip
plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.garantibbva.processarchitecture.tools"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Eklenti yalnizca platform API'si kullanir; Community ile derlenir, Ultimate'ta da calisir.
        intellijIdeaCommunity("2024.1")
    }
}

sourceSets {
    main {
        java.srcDir("../converter-core/src/main/java")
        resources.srcDir("../converter-core/src/main/resources")
    }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

intellijPlatform {
    pluginConfiguration {
        name = "Avrasya to BAMOE BPM Converter"
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
