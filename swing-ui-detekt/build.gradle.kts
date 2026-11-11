// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import buildsrc.kotlinDefaults
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.publishing")
    id("buildsrc.convention.jacoco-coverage")
}

// Rules run inside detekt, not inside a composition, so nothing here is composed and the module takes
// the shared settings directly rather than the `kotlin-jvm` convention, which adds the Compose plugin.
kotlinDefaults()

// A module cannot contribute its own ruleset to itself, so the sections detekt.yml configures for it
// are not on this module's detekt classpath and would fail config validation.
detekt {
    config.setFrom(
        rootProject.file("config/detekt/detekt.yml"),
        rootProject.file("config/detekt/rule-provider-overrides.yml"),
    )
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {}
}

dependencies {
    // detekt supplies its API to the rules it loads; carrying it at runtime would load a second copy.
    compileOnly(libs.detekt.api)

    // The Compose rules travel with this artifact: a consumer adding it to detektPlugins gets them on
    // the same classpath, already pointed at this library's types by the config this jar carries.
    api(libs.composeRulesDetekt)

    // detekt-test asks for detekt-api's test-fixtures capability at runtime, but detekt 2.0.0-alpha
    // publishes only the sources jar of those fixtures, so that variant cannot resolve. The fixtures
    // back detekt-test's file-process-listener helpers alone, which nothing here touches.
    testImplementation(libs.detekt.test) {
        exclude(group = "dev.detekt", module = "detekt-api")
    }
    testImplementation(libs.detekt.api)
    testImplementation(kotlin("test"))
}

// Leave headroom below current coverage; do not chase unreachable PSI branches with empty tests.
jacocoCoverage {
    lineMinimum.set("0.95".toBigDecimal())
    branchMinimum.set("0.80".toBigDecimal())
}

publishing {
    publications.named<MavenPublication>("maven") {
        pom {
            name.set("compose-swing-ui-detekt")
            description.set("detekt rules for the compose-swing-ui API.")
        }
    }
}
