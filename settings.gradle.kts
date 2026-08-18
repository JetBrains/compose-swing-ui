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

include(":samples:docs")
include(":samples:todo-app")
include(":samples:widgets-gallery")
include(":swing-ui")
include(":swing-ui-preview-host")
include(":swing-ui-animation")
include(":swing-ui-test")
include(":idea-plugin")

rootProject.name = "compose-swing-ui"
