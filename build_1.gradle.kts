plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.cts.plugin.intelij"
version = "1.0.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.3")           // Minimum supported IntelliJ version
    type.set("IC")                  // IC = IntelliJ IDEA Community; IU = Ultimate
    plugins.set(listOf())
}

dependencies {
    // Gson for JSON serialization (bundled in IntelliJ platform, but explicit for build)
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        sinceBuild.set("233")       // IntelliJ 2023.3
        untilBuild.set("241.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN") ?: "")
        privateKey.set(System.getenv("PRIVATE_KEY") ?: "")
        password.set(System.getenv("PRIVATE_KEY_PASSWORD") ?: "")
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN") ?: "")
    }
}