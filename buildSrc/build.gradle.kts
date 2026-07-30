import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    // buildSrc is an included build, so root-level ktlintCheck/detekt do NOT reach it; CI invokes
    // :buildSrc:ktlintCheck and :buildSrc:detekt explicitly to enforce this.
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

extensions.getByType<KotlinBaseExtension>().explicitApi()

dependencies {
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.kotlinComposePlugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    // The standalone Android Lint plugin, so the kotlin-quality convention plugin can apply it to JVM
    // modules and host the Compose lint checks without pulling in AGP or the Android SDK.
    implementation(libs.androidLint.gradle.plugin)
    // Contributes each publishing module's publications to the root project's Central Portal aggregation.
    implementation(libs.nmcp.gradle.plugin)
}

// buildSrc is an included build and cannot apply its own precompiled `kotlin-quality` convention
// plugin, so the same ktlint/detekt gates it applies to modules are restated here.
ktlint {
    version.set(libs.versions.ktlint.asProvider())
    ignoreFailures.set(false)
    filter {
        exclude { it.file.path.contains("/build/generated-sources/") }
    }
}

detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootDir.resolve("../config/detekt/detekt.yml"))
    ignoreFailures.set(false)
    parallel.set(true)
}

// Pin the bytecode target without provisioning a JDK toolchain, so the build runs on any JDK >= 21
// while still producing JVM 21 class files.
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    compilerOptions.allWarningsAsErrors.set(true)
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget.set("21")
}
