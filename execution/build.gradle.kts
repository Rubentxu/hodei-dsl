plugins {
    kotlin("jvm")
    `maven-publish`
}

description = "Hodei Pipeline DSL - Execution Engine"

dependencies {
    // Core and steps modules
    api(project(":core"))
    implementation(project(":steps"))
    
    // Metrics and monitoring
    implementation(libs.micrometer.core)
    implementation(libs.micrometer.prometheus)
    
    // Distributed tracing
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    
    // Kotlin ecosystem (advanced coroutines)
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.kotlinx.coroutines.slf4j)
    
    // Logging
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)
    
    // Testing
    testImplementation(libs.kotest.extensions.testcontainers)
    testImplementation(libs.kotlinx.coroutines.test)
}