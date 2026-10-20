import dev.detekt.gradle.Detekt

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.window-system-lock")
    application
}

dependencies {
    implementation(project(":swing-ui"))
    implementation(project(":swing-ui-foundation"))
    implementation(project(":swing-ui-animation"))
    implementation(libs.flatlaf)
    implementation(libs.kotlinxCoroutinesSwing)
    implementation(libs.flatlaf.intellijThemes)
    implementation(libs.flatlaf.extras)
    implementation(libs.ikonli.swing)
    implementation(libs.ikonli.featherPack)
    implementation(libs.markdown)
    implementation(libs.androidxRoom3Runtime)
    implementation(libs.androidxSqliteBundled)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.composeRuntimeSaveable)
    ksp(libs.androidxRoom3Compiler)
    compileOnly(libs.jetbrainsAnnotations)

    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-test"))
}

// The committed schema lets Room check each release's database against the last.
room3 {
    schemaDirectory("$projectDir/schemas")
}

// Room generates Kotlin sources under build/generated/ksp; the checks cover the hand-written code only.
ktlint {
    filter {
        exclude { it.file.path.contains("/build/generated/ksp/") }
    }
}

tasks.withType<Detekt>().configureEach {
    exclude { it.file.path.contains("/build/generated/ksp/") }
}

application {
    mainClass = "org.jetbrains.compose.swing.samples.agent.AgentMainKt"
    // The bundled SQLite driver loads its native library.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
