plugins {
    id("buildsrc.convention.kotlin-jvm")
    `maven-publish`
}

description = "Hodei Pipeline DSL - Kotlin Script Compiler"

dependencies {
    // Core module
    api(project(":core"))
    
    // Kotlin scripting - use available dependencies
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.dependencies)
    implementation(libs.kotlin.scripting.dependencies.maven)
    
    // Additional Kotlin scripting dependencies (hardcoded versions matching kotlin version)
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:${libs.versions.kotlin.get()}")
    
    // Code generation
    implementation(libs.kotlinpoet)
    
    // Kotlin ecosystem
    implementation(libs.bundles.kotlinxEcosystem)
    
    // Testing dependencies
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    
    // Test runtime
    testRuntimeOnly(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
    
    // Increase test timeout for compilation tests
    systemProperty("kotest.timeout", "300000") // 5 minutes
    
    // JVM options for script compilation
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED"
    )
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        // Add scripting-specific opt-ins to the convention plugin defaults
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.script.experimental.api.ExperimentalScriptingApi"
        )
    }
}