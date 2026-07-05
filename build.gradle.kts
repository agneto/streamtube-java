import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.spotless)
}

allprojects {
    group = "com.streamtube"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "com.diffplug.spotless")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
        }
    }

    dependencies {
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        // Boot 3.5 ships JUnit 5.12+, which requires the platform launcher on the test runtime
        // classpath to stay version-aligned with the engine (Gradle no longer injects it).
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    spotless {
        java {
            target("src/**/*.java")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
