plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.knit)
}

// Compiles the Kotlin example files Knit generates from the fenced snippets in the repository's
// Markdown, so the build checks what the documentation shows. The module has no sources of its own:
// everything it compiles is generated into its build directory. Generated code is not hand-written
// code, so the shared ktlint/detekt/lint, publishing, coverage and ABI gates stay off this module.

knit {
    // Only the documents that carry Knit directives. The default file set walks the whole checkout,
    // generated sources under build directories included.
    files =
        fileTree(project.rootDir) {
            include("README.md")
            include("docs/*.md")
        }
}

// Knit's output directory, named by `knit.dir` in the repository's knit.properties. The Knit task
// itself declares no outputs, and files a task writes without declaring them stay stale in Gradle's
// view of the file system - a following compile would keep reporting itself up to date over sources
// Knit has just rewritten. Naming the directory here is what makes an edited snippet reach the
// compiler.
val knitOutputDir = layout.buildDirectory.dir("generated/knit")

val knitTask =
    tasks.named("knit") {
        outputs.dir(knitOutputDir)
    }

kotlin.sourceSets.main {
    kotlin.srcDir(knitOutputDir)
}

tasks.compileKotlin {
    dependsOn(knitTask)
}

// `knitCheck` compares the generated files against what the Markdown would produce now. That can only
// differ when the files are kept in the repository; here they are produced from the Markdown on every
// run, so the comparison has nothing left to catch - and `check` orders it against no other task, so
// on a clean build directory it can just as well run before the files exist and fail. Compiling the
// generated sources is the gate that replaces it.
tasks.named("check") {
    setDependsOn(dependsOn.filterNot { (it as? TaskProvider<*>)?.name == "knitCheck" })
}

dependencies {
    implementation(project(":swing-ui"))
    implementation(project(":swing-ui-animation"))
    implementation(project(":swing-ui-test"))
    // The generated examples live in the main source set, where nothing runs them; the harness snippets
    // still name `kotlin.test.Test` and JUnit's assumptions, so the framework binding has to be explicit
    // here rather than inherited from a test task.
    implementation(kotlin("test-junit5"))
}
