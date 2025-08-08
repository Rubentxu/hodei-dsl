plugins {
    kotlin("jvm")
    `maven-publish`
}

description = "Hodei Pipeline DSL - Plugin System"

dependencies {
    // Core and compiler modules
    api(project(":core"))
    api(project(":compiler"))
    
    // Code generation
    implementation(libs.kotlinpoet)
    
    // Reflection and classloading
    implementation(libs.kotlin.reflect)
    implementation(libs.classgraph)
    
    // HTTP client for plugin downloads
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // Kotlin ecosystem
    implementation(libs.bundles.kotlinxEcosystem)
    
}