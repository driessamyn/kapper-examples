plugins {
    id("java")
}

group = "net.samyn.kapper.example"
version = "1.0-SNAPSHOT"

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
    implementation(libs.kapper) // core kapper library

    testImplementation(libs.bundles.test)
    testImplementation(testFixtures(project(":kotlin-example")))

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.bundles.dbs)
    testRuntimeOnly(libs.slf4j.simple)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    systemProperty("org.slf4j.simpleLogger.log.org.testcontainers", "WARN")
    systemProperty("org.slf4j.simpleLogger.log.net.samyn", "TRACE")
}
