plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    application
}

dependencies {
    implementation(project(":swing-ui"))
}

application {
    mainClass = "org.jetbrains.compose.swing.passcost.PassCostKt"
    // Headless: nothing here is ever shown, so no window is created and no window-system lock is taken.
    applicationDefaultJvmArgs = listOf("-Xmx1g", "-Djava.awt.headless=true")
}
