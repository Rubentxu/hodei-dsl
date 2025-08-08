plugins {
    kotlin("jvm")
    `maven-publish`
}

description = "Hodei Pipeline DSL - Pipeline Steps Implementation"

dependencies {
    // Core module
    api(project(":core"))
    
    // Process execution
    implementation(libs.zt.exec)
    
    // File operations
    implementation(libs.commons.io)
    
    // Docker client
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)
    
    // Kubernetes client
    implementation(libs.kubernetes.client)
    
    // Kotlin ecosystem
    implementation(libs.bundles.kotlinxEcosystem)
    
    // Testing
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
}