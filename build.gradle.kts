plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.cts.plugin.intellij.loc"
version = "1.0.0"

// Java source files live under src/main/kotlin — add them to the Java compile path
sourceSets {
    main {
        java.srcDirs("src/main/kotlin")
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Gson for JSON serialisation of events sent to backend
    implementation("com.google.code.gson:gson:2.11.0")

    intellijPlatform {
        create("IC", "2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    // JUnit 5 for unit testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")

    // JUnit 5 Suite for test organization
    testImplementation("org.junit.platform:junit-platform-suite:1.10.2")
    testImplementation("org.junit.platform:junit-platform-suite-api:1.10.2")
    testImplementation("org.junit.platform:junit-platform-suite-engine:1.10.2")

    // Mockito for mocking (optional, for advanced testing)
    testImplementation("org.mockito:mockito-core:5.7.1")
    testImplementation("org.mockito:mockito-junit-jupiter:5.7.1")

    // AssertJ for fluent assertions (optional, for advanced testing)
    testImplementation("org.assertj:assertj-core:3.27.7")
}

intellijPlatform {
    pluginConfiguration {
        name = "GenAI LOC Tracker"
        version = "1.0.0"

        description = """
            Tracks Lines of Code (LOC) generated with GenAI coding assistants
            (Copilot, Claude, ChatGPT, Gemini, etc.) inside IntelliJ IDEA.
            Events are posted to a backend REST API. When the service is offline,
            events are saved to a local CSV file and replayed automatically on recovery.
        """.trimIndent()

        ideaVersion {
            sinceBuild = "251"
            untilBuild = "253.*"
        }

        changeNotes = """
            <b>1.0.0</b>
            <ul>
                <li>Initial release</li>
                <li>LOC tracking per file change</li>
                <li>GenAI tool auto-detection</li>
                <li>CSV fallback when backend is offline</li>
                <li>Auto-replay on backend recovery</li>
            </ul>
        """.trimIndent()
    }

    // signing and publishing only needed for JetBrains Marketplace — skip for local use
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.release.set(21)   // enforce class file version 65.0 even when built with JDK 22
    }

    // ── Skip slow tasks not needed for local dev ──────────────────────────
    // buildSearchableOptions scans all Settings UI — takes ~2 min, only needed for Marketplace publish
    named("buildSearchableOptions") {
        enabled = !project.hasProperty("skipBuildSearchableOptions")
    }

    // ── runIde tuning ─────────────────────────────────────────────────────
    named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
        // More heap for the sandbox IDE — avoids GC pauses during startup
        jvmArgumentProviders.add(
            CommandLineArgumentProvider {
                listOf(
                    "-Xmx1536m",
                    "-Xms256m",
                    "-XX:+UseG1GC",
                    // Disable slow index-sharing download on first run
                    "-Didea.shared.indexes.download=false",
                    // Don't check for IDE updates in sandbox
                    "-Didea.updates.url=",
                    // Speed up AWT startup on Windows
                    "-Dawt.useSystemAAFontSettings=lcd"
                )
            }
        )
    }

    test {
        useJUnitPlatform()
        
        // Display test results
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }

    // Bundle Gson into the plugin ZIP (not provided by IntelliJ platform)
    buildPlugin {
        archiveBaseName.set("genai-loc-tracker")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
