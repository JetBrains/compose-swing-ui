// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    application
}

kotlin {
    sourceSets.configureEach {
        languageSettings.optIn("org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi")
    }
}

dependencies {
    implementation(project(":swing-ui"))

    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-test"))
}

application {
    mainClass = "org.jetbrains.compose.swing.samples.todo.TodoAppKt"
}
