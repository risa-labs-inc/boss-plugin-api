import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "ai.rever.boss.plugin.bundled"
version = "1.0.14"

repositories {
    mavenCentral()
    google()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // BOSS Plugin API from Maven Central
    implementation("com.risaboss:plugin-api-desktop:1.0.14")

    // Compose runtime for UI components
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Serialization for manifest parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

// Task to build the bundled plugin JAR
tasks.register<Jar>("buildPluginJar") {
    group = "build"
    description = "Creates the plugin JAR for distribution"

    archiveBaseName.set("boss-plugin-api")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")

    from(sourceSets.main.get().output)

    // Include resources (especially META-INF/boss-plugin/plugin.json)
    from("src/main/resources")

    // Ensure plugin.json is in the correct location
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Implementation-Title" to "BOSS Plugin API",
            "Implementation-Version" to version,
            "Implementation-Vendor" to "Risa Labs Inc.",
            "Plugin-Id" to "ai.rever.boss.plugin.api",
            "Plugin-Version" to version
        )
    }
}

// Build the plugin JAR when building the project
tasks.named("build") {
    dependsOn("buildPluginJar")
}

// Task to copy the plugin JAR to the BOSS plugins directory for testing
tasks.register<Copy>("installLocal") {
    group = "distribution"
    description = "Installs the plugin JAR to ~/.boss/plugins/ for local testing"

    dependsOn("buildPluginJar")

    from(layout.buildDirectory.dir("libs")) {
        include("boss-plugin-api-*.jar")
        exclude("*-sources.jar", "*-javadoc.jar")
    }

    into(System.getProperty("user.home") + "/.boss/plugins")

    doLast {
        println("Installed plugin to: ${System.getProperty("user.home")}/.boss/plugins/")
    }
}
