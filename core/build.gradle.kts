plugins {
    kotlin("jvm")
    `maven-publish`
}

description = "Hodei Pipeline DSL - Core Domain and APIs"

dependencies {
    // Kotlin reflection for DSL introspection
    implementation(libs.kotlin.reflect)
    
    // Additional core-specific dependencies
    implementation(libs.bundles.kotlinxEcosystem)
}