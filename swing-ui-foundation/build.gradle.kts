import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    `java-test-fixtures`
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.publishing")
    id("buildsrc.convention.jacoco-coverage")
    id("buildsrc.convention.window-system-lock")
    id("buildsrc.convention.exclusive-window-system-tests")
    id("buildsrc.convention.compiler-test-harness")
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {}

    sourceSets.configureEach {
        languageSettings.optIn("org.jetbrains.compose.swing.annotations.InternalSwingUiApi")
    }
}

dependencies {
    // @FloatRange on public declarations, mirroring upstream ui and foundation's api dependency.
    api(libs.androidxAnnotation)
    api(project(":swing-ui"))

    // Fixtures the animation module's tests reuse.
    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(testFixtures(project(":swing-ui")))
    testFixturesImplementation(project(":swing-ui-test"))

    testImplementation(kotlin("test"))
    testImplementation(libs.junitJupiterParams)
    testImplementation(testFixtures(project(":swing-ui")))
    testImplementation(project(":swing-ui-test"))
}

// The fixtures serve this project's tests and those of projects that depend on them; the library does not
// publish them.
(components["java"] as AdhocComponentWithVariants).run {
    withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}

jacocoCoverage {
    lineMinimum.set("0.95".toBigDecimal())
    branchMinimum.set("0.85".toBigDecimal())
}

publishing {
    publications.named<MavenPublication>("maven") {
        pom {
            name.set("compose-swing-ui-foundation")
            description.set(
                "Foundation layout primitives for compose-swing-ui.",
            )
        }
    }
}
