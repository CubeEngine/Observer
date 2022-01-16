import org.spongepowered.gradle.plugin.config.PluginLoaders
import org.spongepowered.plugin.metadata.model.PluginDependency

plugins {
    `java-library`
    id("org.spongepowered.gradle.plugin") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "org.cubeengine.plugin"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val prometheusVersion = "0.14.1"
    implementation("io.netty:netty-codec-http:4.1.72.Final")
    implementation("io.prometheus:simpleclient:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.jaegertracing:jaeger-client:1.7.0")
    compileOnly("org.spongepowered:observer:1.0-SNAPSHOT")
}

sponge {
    apiVersion("8.0.0")
    license("MIT")
    loader {
        name(PluginLoaders.JAVA_PLAIN)
        version("1.0")
    }
    plugin("observer") {
        displayName("Observer")
        entrypoint("org.cubeengine.plugin.observer.Observer")
        description("Plugin to export sponge metrics as prometheus endpoint")
        links {
            homepage("https://cubeengine.org")
            // source("https://spongepowered.org/source")
            // issues("https://spongepowered.org/issues")
        }
        contributor("pschichtel") {
            description("Author")
        }
        dependency("spongeapi") {
            loadOrder(PluginDependency.LoadOrder.AFTER)
            optional(false)
        }
    }
}

val javaTarget = 11 // Sponge targets a minimum of Java 8
java {
    sourceCompatibility = JavaVersion.toVersion(javaTarget)
    targetCompatibility = JavaVersion.toVersion(javaTarget)
    if (JavaVersion.current() < JavaVersion.toVersion(javaTarget)) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaTarget))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.apply {
        encoding = "utf-8"
        release.set(javaTarget)
    }
}

// Make sure all tasks which produce archives (jar, sources jar, javadoc jar, etc) produce more consistent output
tasks.withType<AbstractArchiveTask>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}
