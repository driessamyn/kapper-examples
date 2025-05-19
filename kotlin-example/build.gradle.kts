plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)

    `java-library`

    // needed for hibernate!
    id("org.jetbrains.kotlin.plugin.jpa") version "2.1.21"
}

repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/driessamyn/kapper")
        credentials {
            username = project.findProperty("gh.user") as String? ?: System.getenv("GH_USERNAME")
            password = project.findProperty("gh.key") as String? ?: System.getenv("GH_TOKEN")
        }
    }
}

dependencies {
    implementation("net.samyn:kapper-coroutines:1.4.0")
    implementation(libs.kotlinx.coroutines.core)
    // alternatives
    //  hibernate
    implementation(libs.bundles.hibernate)
    //  ktorm
    implementation(libs.bundles.ktorm)
    // test
    testImplementation(libs.bundles.test)
    testImplementation(libs.hikari)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.bundles.dbs)
    testRuntimeOnly(libs.slf4j.simple)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.check {
    dependsOn(tasks.ktlintCheck)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(tasks.ktlintFormat)
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    systemProperty("org.slf4j.simpleLogger.log.org.testcontainers", "WARN")
    systemProperty("org.slf4j.simpleLogger.log.net.samyn", "TRACE")
}
