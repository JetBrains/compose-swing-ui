import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.publishing")
    id("buildsrc.convention.jacoco-coverage")
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
        filters {
            exclude {
                annotatedWith.add("org.jetbrains.compose.swing.annotations.InternalSwingUiApi")
            }
        }
    }

    sourceSets.configureEach {
        languageSettings.optIn("org.jetbrains.compose.swing.annotations.InternalSwingUiApi")
    }
}

lint {
    // This module is test infrastructure. An API marked visible for testing - androidx's
    // LifecycleRegistry.createUnsafe among them - is reached here by the audience it was widened for,
    // never from production code.
    disable += "VisibleForTests"
}

dependencies {
    api(project(":swing-ui"))
    api(libs.composeRuntime)
    api(kotlin("test"))
    api(libs.kotlinxCoroutinesTest)
    api(libs.kotlinxCoroutinesSwing)
    // @Nls localization annotations. CLASS/IDE-only: compileOnly so they warn consumers in-IDE across
    // the jar boundary without leaking org.jetbrains:annotations to the published runtime.
    compileOnly(libs.jetbrainsAnnotations)
    // The harness's own tests drive real animations to show what manual frame control does to one.
    // Test-only: the published harness does not depend on the animation module.
    testImplementation(project(":swing-ui-animation"))
}

jacocoCoverage {
    lineMinimum.set("0.85".toBigDecimal())
    branchMinimum.set("0.80".toBigDecimal())
}

publishing {
    publications.named<MavenPublication>("maven") {
        pom {
            name.set("compose-swing-ui-test")
            description.set(
                "Headless test harness for driving and asserting compose-swing-ui compositions.",
            )
        }
    }
}
