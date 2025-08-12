# 🚀 Hodei Pipeline DSL

> A modern Kotlin-based declarative pipeline DSL inspired by Jenkins but built for cloud-native environments with enhanced type safety, coroutines, and extensibility.

[![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-02303A.svg?style=for-the-badge&logo=Gradle&logoColor=white)](https://gradle.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

## 🌟 Features

- **🎯 Type-Safe DSL**: Compile-time safety for pipeline definitions with Kotlin's powerful type system
- **⚡ Coroutines & Async**: Built on Kotlin coroutines for efficient concurrent execution
- **🔧 SOLID Architecture**: Clean architecture following SOLID principles with Strategy Pattern for step handlers
- **🏗️ Hexagonal Design**: Domain-driven design with clear separation of concerns
- **🔌 Extensible**: Plugin system for custom step types and integrations
- **📊 Observability**: Built-in metrics, logging, and event system
- **🐳 Cloud-Native**: Designed for containerized environments with Kubernetes and Docker support
- **🔄 Jenkins Compatibility**: Conceptual compatibility with Jenkins Declarative Pipeline syntax

## 🏗️ Architecture

Hodei DSL follows a modular hexagonal architecture:

```
┌─────────────────────────────────────────────────────────┐
│                        DSL Layer                        │
├─────────────────────────────────────────────────────────┤
│                     Domain Model                        │
│           (Pipeline, Stage, Step, Agent...)            │
├─────────────────────────────────────────────────────────┤
│                   Execution Engine                      │
│     (PipelineExecutor, StepHandlers, Context...)       │
├─────────────────────────────────────────────────────────┤
│              Infrastructure Layer                       │
│        (Compiler, CLI, Plugins, Adapters...)           │
└─────────────────────────────────────────────────────────┘
```

## 🚀 Quick Start

### Basic Pipeline Example

```kotlin
val pipeline = pipeline {
  agent { any() }
  
  environment {
    set("JAVA_HOME", "/opt/jdk")
    set("GRADLE_OPTS", "-Xmx2g")
  }

  stage("Build") {
    steps {
      sh("./gradlew clean build")
      archiveArtifacts("build/libs/*.jar", fingerprint = true)
    }
  }

  stage("Test") {
    steps {
      sh("./gradlew test")
      publishTestResults("build/test-results/test/*.xml")
    }
  }
  
  stage("Deploy") {
    when {
      branch("main")
      environment("DEPLOY_ENV", "production")
    }
    steps {
      sh("./deploy.sh")
    }
  }
}
```

### Advanced Features

```kotlin
pipeline {
  stage("Parallel Testing") {
    steps {
      parallel {
        branch("unit-tests") {
          sh("./gradlew test")
        }
        branch("integration-tests") {
          sh("./gradlew integrationTest")
        }
        branch("lint-check") {
          sh("./gradlew ktlintCheck")
        }
      }
      
      retry(times = 3) {
        sh("./gradlew flakyTest")
      }
      
      timeout(minutes = 10) {
        sh("./gradlew longRunningTask")
      }
    }
  }
}
```

## 📦 Project Structure

```
hodei-dsl/
├── core/           # Domain model, DSL builders, and execution engine
├── compiler/       # Kotlin script compilation and dependency resolution
├── execution/      # Extended execution implementations and runtime
├── steps/          # Built-in step implementations
├── plugins/        # Plugin system and extensions
├── cli/            # Command-line interface
├── library/        # Programmatic API for embedding
├── examples/       # Example pipeline scripts
└── docs/           # Comprehensive documentation
```

## 🛠️ Build & Development

### Prerequisites

- **Java 17+**
- **Kotlin 1.9+**
- **Gradle 8+**

### Building the Project

```bash
# Build all modules
gradle build

# Run tests
gradle test

# Run checks (tests, linting, static analysis)
gradle check

# Clean build outputs
gradle clean

# Build and run CLI
gradle :cli:run --args="--help"
```

### Testing

```bash
# Run core module tests only
gradle :core:test

# Run specific test
gradle :core:test --tests "dev.rubentxu.hodei.core.execution.handlers.EchoStepHandlerSpec"

# Run handler tests only
gradle :core:test --tests "*Handler*"
```

## 📋 Current Status

### ✅ Completed (FASE 1-2)
- **Domain Model**: Immutable Pipeline/Stage/Step with validation
- **DSL Builders**: Type-safe DSL with compile-time safety
- **SOLID Architecture**: Strategy Pattern for step handlers
- **Core Execution**: Pipeline/Stage/Step executors with fault tolerance
- **Simple Step Handlers**: Echo, Shell, ArchiveArtifacts, PublishTestResults, Stash, Unstash
- **Testing Framework**: Comprehensive test suite with Kotest

### 🚧 In Progress (FASE 3-5)
- **Complex Step Handlers**: Dir, WithEnv, Parallel, Retry, Timeout
- **Context Receivers**: Modern Kotlin DSL patterns
- **Legacy Code Cleanup**: Refactoring legacy implementations

### 🔮 Planned Features
- **Plugin System**: Dynamic plugin loading and DSL extensions
- **IDE Integration**: IntelliJ IDEA plugin for syntax highlighting and completion
- **Web UI**: Pipeline visualization and monitoring dashboard
- **Cloud Integrations**: Native Kubernetes, Docker, and cloud provider support

## 📚 Documentation

- **[Core Module](./core/README.md)** - Domain model and execution engine
- **[Architecture](./docs/architecture.md)** - System design and patterns
- **[DSL Specification](./docs/dsl-specification.md)** - Language syntax and semantics
- **[Plugin System](./docs/plugin-system.md)** - Extensibility and plugins
- **[Examples](./examples/)** - Sample pipeline scripts

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](./CONTRIBUTING.md) for details.

### Development Workflow

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Follow our [TDD workflow](./docs/tdd-workflow.md)
4. Run tests: `gradle test`
5. Commit changes: `git commit -m 'feat: add amazing feature'`
6. Push to branch: `git push origin feature/amazing-feature`
7. Open a Pull Request

### Code Standards

- **Kotlin Coding Conventions**: Follow official Kotlin style guide
- **SOLID Principles**: Apply SOLID design principles
- **Test Coverage**: Maintain high test coverage with meaningful tests
- **Documentation**: Document public APIs with KDoc

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Inspired by Jenkins Declarative Pipeline
- Built with [Kotlin](https://kotlinlang.org/) and [Gradle](https://gradle.org/)
- Testing with [Kotest](https://kotest.io/)
- Documentation with [Mermaid](https://mermaid.js.org/)

---

**Hodei** (pronounced "ho-day") means "cloud" in Basque, reflecting our cloud-native philosophy. ☁️