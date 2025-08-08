plugins {
    kotlin("jvm")
    `maven-publish`
}

description = "Hodei Pipeline DSL - Embedded Library API"

dependencies {
    // All core modules except CLI
    api(project(":core"))
    api(project(":compiler"))
    api(project(":steps"))
    api(project(":plugins"))
    api(project(":execution"))
    
    // Spring integration (optional)
    compileOnly(libs.spring.boot.starter)
    compileOnly(libs.spring.boot.autoconfigure)
    
    // Reactive streams
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.kotlinx.coroutines.reactor)
    
    // Kotlin ecosystem
    implementation(libs.bundles.kotlinxEcosystem)
    
    // Testing
    testImplementation(libs.spring.boot.starter.test)
}