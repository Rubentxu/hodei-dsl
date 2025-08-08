plugins {
    kotlin("jvm")
    application
}

description = "Hodei Pipeline DSL - Command Line Interface"

application {
    mainClass.set("dev.rubentxu.hodei.cli.MainKt")
}

dependencies {
    // All core modules
    implementation(project(":core"))
    implementation(project(":compiler"))
    implementation(project(":steps"))
    implementation(project(":plugins"))
    implementation(project(":execution"))
    
    // CLI framework
    implementation(libs.clikt)
    
    // File watching and NIO support
    implementation(libs.kotlinx.coroutines.jdk8)
    
    // Configuration
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)
    
    // Kotlin ecosystem
    implementation(libs.bundles.kotlinxEcosystem)
    
    // Logging
    implementation(libs.bundles.logging)
}

tasks.jar {
    archiveBaseName.set("hodei-cli")
}