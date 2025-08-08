plugins {
    kotlin("jvm")
    `maven-publish`
}

description = "Hodei Pipeline DSL - Kotlin Script Compiler"

dependencies {
    // Core module
    api(project(":core"))
    
    // Kotlin scripting
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.dependencies)
    implementation(libs.kotlin.scripting.dependencies.maven)
    
    // Code generation
    implementation(libs.kotlinpoet)
    
    // Kotlin ecosystem
    implementation(libs.bundles.kotlinxEcosystem)
    
}