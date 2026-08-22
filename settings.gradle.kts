@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":benchmarks:PassCost")
include(":benchmarks:SwingMark")
include(":samples:docs")
include(":samples:todo-app")
include(":samples:widgets-gallery")
include(":swing-ui")
include(":swing-ui-animation")
include(":swing-ui-test")

rootProject.name = "compose-swing-ui"
