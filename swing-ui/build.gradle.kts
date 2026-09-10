// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
    `java-library`
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.publishing")
}

dependencies {
    api(project(":swing-ui-core"))
    api(project(":swing-ui-components"))
    api(project(":swing-ui-foundation"))
    api(project(":swing-ui-animation"))
    api(project(":swing-ui-svg"))
}

publishing {
    publications.named<MavenPublication>("maven") {
        pom {
            name.set("compose-swing-ui")
            description.set(
                "Umbrella artifact for compose-swing-ui: core, components, foundation, animation, and SVG.",
            )
        }
    }
}
