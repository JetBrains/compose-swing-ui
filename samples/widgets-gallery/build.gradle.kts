// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.window-system-lock")
    application
}

dependencies {
    implementation(project(":swing-ui"))
    implementation(project(":swing-ui-foundation"))
    implementation(project(":swing-ui-animation"))
    implementation(libs.androidxNavigation3Runtime)

    testImplementation(kotlin("test"))
    testImplementation(libs.junitJupiterParams)
    testImplementation(project(":swing-ui-test"))
    testImplementation(testFixtures(project(":swing-ui")))
}

application {
    mainClass = "org.jetbrains.compose.swing.samples.widgets.WidgetsGalleryMainKt"
}
