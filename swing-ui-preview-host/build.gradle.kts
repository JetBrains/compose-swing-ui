plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.jacoco-coverage")
}

jacocoCoverage {
    lineMinimum.set("0.85".toBigDecimal())
    branchMinimum.set("0.80".toBigDecimal())
}

dependencies {
    // The host is placed on the previewed project's own runtime classpath, so every one of these
    // resolves there, at the version that project builds against. Bundling any of them into the host
    // jar would shadow the user's own copy and render a composition the compiler never saw.
    compileOnly(project(":swing-ui"))
    compileOnly(libs.composeRuntime)
    compileOnly(libs.kotlinxCoroutinesSwing)

    testImplementation(project(":swing-ui"))
    testImplementation(libs.composeRuntime)
    testImplementation(libs.kotlinxCoroutinesSwing)
    testImplementation(kotlin("test"))
    // Stands in for the theme library a previewed project brings: what these cases measure is a look
    // and feel that is neither the JDK's nor this repository's, reached only through the classpath.
    testImplementation(libs.flatlaf)
}
