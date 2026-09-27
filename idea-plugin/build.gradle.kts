import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// This module is deliberately outside the library's own conventions: it publishes nothing, dumps no
// ABI, and targets the JDK the IntelliJ Platform requires rather than the library's Java 11 floor.
plugins {
    kotlin("jvm")
    alias(libs.plugins.intellijPlatform)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(libs.versions.intellijIdea.get())
        // Kotlin for the PSI a preview is found in; Java for the module/PSI classes it rests on and for
        // the compiler and SDK a render needs.
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
    // Bundled into the plugin distribution so its jar can be put on a preview process's classpath.
    // Nothing in it is ever loaded by the IDE itself.
    implementation(project(":swing-ui-preview-host"))
    testImplementation(kotlin("test"))
}

// The platform provides the Kotlin standard library; a second copy in the plugin's own lib directory
// would be loaded by the plugin class loader and shadow it.
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

intellijPlatform {
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, libs.versions.intellijIdea.get())
        }
    }

    pluginConfiguration {
        name = "Compose Swing UI"
        // The same coordinate the library publishes under, so a plugin build and a library build of one
        // commit carry the same version.
        version = providers.gradleProperty("version").orElse("0.1.0-SNAPSHOT")
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
}
