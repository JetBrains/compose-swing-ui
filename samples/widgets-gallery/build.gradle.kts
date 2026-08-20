plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    application
}

dependencies {
    implementation(project(":swing-ui"))
    implementation(project(":swing-ui-animation"))
    implementation(libs.androidxNavigation3Runtime)

    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-test"))
}

application {
    mainClass = "org.jetbrains.compose.swing.samples.widgets.WidgetsGalleryMainKt"
}
