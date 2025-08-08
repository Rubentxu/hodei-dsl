// Hodei Pipeline DSL - Library Module Convention Plugin
// Configuration for library modules that will be published
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
    signing
}

group = "dev.rubentxu.hodei"
version = project.findProperty("version") ?: "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    explicitApi()
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-Xjsr305=strict",
            "-Xjvm-default=all",
            "-Xcontext-receivers"
        )
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STARTED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    
    minHeapSize = "512m"
    maxHeapSize = "2g"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
}

java {
    withSourcesJar()
    withJavadocJar()
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set(project.name)
                description.set(project.description ?: "Hodei Pipeline DSL - ${project.name} module")
                url.set("https://github.com/rubentxu/hodei-dsl")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        name.set("Rubén Díez García")
                        email.set("rubentxu@gmail.com")
                    }
                }
                
                scm {
                    url.set("https://github.com/rubentxu/hodei-dsl")
                    connection.set("scm:git:git://github.com/rubentxu/hodei-dsl.git")
                    developerConnection.set("scm:git:ssh://github.com/rubentxu/hodei-dsl.git")
                }
            }
        }
    }
}

// Only sign if we have signing configuration
if (project.hasProperty("signing.keyId")) {
    signing {
        sign(publishing.publications["maven"])
    }
}