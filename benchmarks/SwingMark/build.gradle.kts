plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    application
}

dependencies {
    implementation(project(":swing-ui"))
    // Writes the captured spans out as a Perfetto trace, which -trace turns on.
    implementation(libs.androidxTracingWire)
    // The gate proving both arms build the same screen: a ratio between them means nothing otherwise.
    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-test"))
}

application {
    // Every timing comes from a run: the suite shows a window and drives it from the main thread. The
    // module's tests time nothing - they gate that both arms build the same screen.
    mainClass = "org.jetbrains.compose.swing.swingmark.SwingMarkKt"
    applicationDefaultJvmArgs = listOf("-Xmx1g")
}
