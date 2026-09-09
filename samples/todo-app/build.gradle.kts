plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    application
}

dependencies {
    implementation(project(":swing-ui"))
    implementation(project(":swing-ui-foundation"))

    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-test"))
}

application {
    mainClass = "org.jetbrains.compose.swing.samples.todo.TodoAppKt"
}
