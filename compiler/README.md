# Compiler Module

The compiler module handles compilation of Hodei DSL scripts (.hodei.kts) into executable pipeline definitions.

## Features

- **Kotlin Script Engine**: Uses Kotlin's scripting engine for .kts file compilation
- **DSL Compilation**: Transforms DSL scripts into pipeline objects
- **Code Generation**: Generates optimized Kotlin code using KotlinPoet
- **Dependency Resolution**: Resolves external dependencies for scripts

## Key Components

- `dev.rubentxu.hodei.compiler.engine` - Script engine configuration
- `dev.rubentxu.hodei.compiler.codegen` - Code generation utilities
- `dev.rubentxu.hodei.compiler.resolver` - Dependency resolution

## Script Format

```kotlin
// example.hodei.kts
pipeline {
    stage("build") {
        sh("./gradlew build")
    }
    stage("test") {
        parallel {
            sh("./gradlew test")
            sh("./gradlew integrationTest")
        }
    }
}
```