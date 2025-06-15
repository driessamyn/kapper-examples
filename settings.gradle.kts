plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kapper-examples"
include("kotlin-example")
include("java-example")

