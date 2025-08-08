// Hodei Pipeline DSL - Application Module Convention Plugin
// Configuration for application modules (CLI, web apps, etc.)
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
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


// Enhanced JAR configuration for applications
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Rubén Díez García",
            "Main-Class" to application.mainClass.orNull
        )
    }
}

// Create fat JAR task for standalone distribution
tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Creates a fat JAR with all dependencies included"
    
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest.attributes["Main-Class"] = application.mainClass.orNull
    
    from(configurations.runtimeClasspath.get().map { 
        if (it.isDirectory) it else zipTree(it) 
    })
    with(tasks.jar.get())
}

// Runtime configuration for applications
tasks.withType<JavaExec>().configureEach {
    // Enable structured concurrency debugging
    systemProperty("kotlinx.coroutines.debug", "on")
    
    // Memory settings for application runtime
    jvmArgs("-Xmx2g", "-XX:+UseG1GC")
}