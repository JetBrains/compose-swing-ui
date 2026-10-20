@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":samples:agent-app")
include(":samples:docs")
include(":samples:todo-app")
include(":samples:widgets-gallery")
include(":swing-ui")
include(":swing-ui-animation")
include(":swing-ui-detekt")
include(":swing-ui-foundation")
include(":swing-ui-test")

rootProject.name = "compose-swing-ui"
