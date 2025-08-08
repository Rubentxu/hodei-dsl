# Flujo de Trabajo TDD/BDD - Hodei Pipeline DSL

## Metodología de Desarrollo

### Test-Driven Development (TDD) + Behavior-Driven Development (BDD)

Este proyecto sigue estrictamente el ciclo **Red-Green-Refactor** con especificaciones BDD usando **Kotest**.

## Workflow Obligatorio por Feature

### 1. **RED Phase** 🔴
```bash
# 1. Crear branch para la feature
git checkout -b feat/pipeline-dsl-core

# 2. Escribir tests que fallan (BDD specs)
# Ejemplo: PipelineSpec.kt
class PipelineSpec : BehaviorSpec({
    given("a new pipeline") {
        when("creating with basic configuration") {
            then("should initialize with default context") {
                // Test que falla inicialmente
            }
        }
    }
})

# 3. Ejecutar tests (deben fallar)
./gradlew test
# ❌ Tests fallan como se espera
```

### 2. **GREEN Phase** 🟢
```bash
# 4. Implementar código mínimo para que pasen los tests
# Crear implementación en el módulo correspondiente

# 5. Ejecutar tests hasta que pasen
./gradlew test
# ✅ Tests pasan
```

### 3. **REFACTOR Phase** 🔄
```bash
# 6. Refactorizar código manteniendo tests verdes
# Aplicar principios SOLID, clean code, etc.

# 7. Ejecutar tests continuamente durante refactor
./gradlew test
# ✅ Tests siguen pasando después del refactor
```

### 4. **COMMIT Phase** 💾
```bash
# 8. Commit con Conventional Commits
git add .
git commit -m "feat(core): add pipeline DSL basic structure

- Implement Pipeline builder with type-safe DSL
- Add ExecutionContext with environment support
- Support for stage definition and execution
- Add comprehensive BDD tests with Kotest

BREAKING CHANGE: Initial API implementation

Closes #1"

# 9. Push y crear PR si es necesario
git push origin feat/pipeline-dsl-core
```

## Estructura de Tests con Kotest

### Convenciones de Naming
```kotlin
// Para specs de comportamiento
class PipelineSpec : BehaviorSpec({
    given("precondición") {
        `when`("acción") {
            then("resultado esperado") {
                // assertions
            }
        }
    }
})

// Para specs de funcionalidad
class StepsSpec : FunSpec({
    test("should execute shell step successfully") {
        // test implementation
    }
    
    context("when executing in Docker context") {
        test("should mount volumes correctly") {
            // test implementation
        }
    }
})

// Para specs de propiedades
class PipelinePropertiesSpec : StringSpec({
    "pipeline name should not be empty" {
        // property-based test
    }
})
```

### Estructura de Directorios de Tests
```
src/test/kotlin/
├── unit/                   # Tests unitarios
│   ├── core/
│   ├── steps/
│   └── plugins/
├── integration/            # Tests de integración
│   ├── pipeline/
│   ├── docker/
│   └── kubernetes/
├── behavior/              # Tests BDD
│   ├── specs/
│   └── features/
└── fixtures/              # Datos de prueba
    ├── pipelines/
    └── configs/
```

## Conventional Commits Standard

### Formato Obligatorio
```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Tipos Permitidos
- `feat`: Nueva funcionalidad
- `fix`: Corrección de bug
- `docs`: Cambios en documentación
- `style`: Formato, espacios, etc. (no afecta código)
- `refactor`: Refactoring de código
- `test`: Agregar o modificar tests
- `chore`: Mantenimiento, deps, etc.
- `perf`: Mejoras de performance
- `ci`: Cambios en CI/CD
- `build`: Cambios en build system

### Scopes por Módulo
- `core`: API central y DSL
- `compiler`: Kotlin script compilation
- `executor`: Motor de ejecución
- `steps`: Implementación de steps
- `plugins`: Sistema de plugins
- `cli`: Interface de línea de comandos
- `api`: API embebida
- `docs`: Documentación

### Ejemplos de Commits
```bash
# Feature nueva
feat(core): add parallel execution support

Implement structured concurrency for pipeline stages using 
Kotlin coroutines with proper error handling and cancellation.

- Add ParallelStage with branch execution
- Implement CoroutineScope management
- Add timeout and cancellation support
- Include comprehensive BDD tests

Closes #15

# Bug fix
fix(steps): resolve shell command encoding issues

Fix UTF-8 encoding problems when executing shell commands
in different locales.

- Use explicit UTF-8 encoding for process streams
- Add charset detection for command output
- Include regression tests

Fixes #23

# Breaking change
feat(api)!: redesign plugin API for better type safety

BREAKING CHANGE: Plugin interface now requires explicit
type parameters for better compile-time validation.

Migration guide:
- Old: class MyPlugin : PipelinePlugin
- New: class MyPlugin : PipelinePlugin<Pipeline>

# Documentation
docs(readme): add installation and quick start guide

# Test addition
test(core): add property-based tests for pipeline validation

# Refactoring
refactor(executor): extract stage execution logic

Extract complex stage execution into separate classes
following Single Responsibility Principle.

- Create StageExecutor hierarchy
- Improve code testability
- Maintain backward compatibility
```

## Workflow de Git

### Branching Strategy
```bash
# Feature branches
feat/feature-name
fix/bug-description
docs/documentation-update
refactor/code-improvement

# Release branches
release/v1.0.0

# Main branches
main        # Código estable y releases
develop     # Desarrollo activo (opcional)
```

### Pre-commit Hooks
```bash
#!/bin/bash
# .git/hooks/pre-commit

# 1. Ejecutar todos los tests
echo "Running tests..."
./gradlew test
if [ $? -ne 0 ]; then
    echo "❌ Tests failed! Commit aborted."
    exit 1
fi

# 2. Verificar formato de código
echo "Checking code format..."
./gradlew ktlintCheck
if [ $? -ne 0 ]; then
    echo "❌ Code format issues! Run ./gradlew ktlintFormat"
    exit 1
fi

# 3. Verificar conventional commits
echo "✅ All checks passed!"
```

## Ciclo de Desarrollo por Feature

### Ejemplo Completo: Implementar Shell Step

#### 1. Crear Specification (RED)
```kotlin
// src/test/kotlin/behavior/steps/ShellStepSpec.kt
class ShellStepSpec : BehaviorSpec({
    given("a shell step with simple command") {
        val step = ShellStep("echo 'Hello World'")
        val context = ExecutionContext.default()
        
        `when`("executing the step") {
            val result = step.execute(context)
            
            then("should return success result") {
                result shouldBe StepResult.Success
            }
            
            then("should log command output") {
                context.logger.output should contain("Hello World")
            }
        }
    }
    
    given("a shell step that fails") {
        val step = ShellStep("exit 1")
        val context = ExecutionContext.default()
        
        `when`("executing the step") {
            val result = step.execute(context)
            
            then("should return failure result") {
                result shouldBe instanceOf<StepResult.Failure>()
            }
        }
    }
})
```

#### 2. Ejecutar Tests (Fallan)
```bash
./gradlew test
# ❌ ShellStepSpec failed: ShellStep class not found
```

#### 3. Implementar Código Mínimo (GREEN)
```kotlin
// src/main/kotlin/core/steps/ShellStep.kt
class ShellStep(
    private val command: String,
    private val returnStdout: Boolean = false
) : PipelineStep {
    
    override suspend fun execute(context: ExecutionContext): StepResult {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(context.workDir.toFile())
                .start()
            
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            
            context.logger.info(output)
            
            if (exitCode == 0) {
                StepResult.Success
            } else {
                StepResult.Failure("Command failed with exit code $exitCode")
            }
        } catch (e: Exception) {
            StepResult.Failure("Failed to execute command: ${e.message}")
        }
    }
}
```

#### 4. Tests Pasan (GREEN)
```bash
./gradlew test
# ✅ All tests passed
```

#### 5. Refactorizar (REFACTOR)
```kotlin
// Aplicar principios SOLID, extraer constantes, mejorar error handling
class ShellStep(
    private val command: String,
    private val returnStdout: Boolean = false,
    private val timeoutSeconds: Long = DEFAULT_TIMEOUT
) : PipelineStep {
    
    companion object {
        private const val DEFAULT_TIMEOUT = 300L
    }
    
    override suspend fun execute(context: ExecutionContext): StepResult {
        return withContext(Dispatchers.IO) {
            executeWithTimeout(context)
        }
    }
    
    private suspend fun executeWithTimeout(context: ExecutionContext): StepResult {
        // Implementación mejorada con timeout y mejor manejo de errores
    }
}
```

#### 6. Commit con Conventional Commits
```bash
git add .
git commit -m "feat(steps): implement ShellStep with timeout support

Add ShellStep implementation with the following features:
- Command execution in separate process
- Configurable timeout with default 5 minutes
- Proper error handling and logging
- Support for return codes and stdout capture

Includes comprehensive BDD tests covering:
- Successful command execution
- Failed command handling
- Timeout scenarios
- Output capturing

Closes #12"
```

## Métricas y Calidad

### Objetivos de Cobertura
- **Cobertura de líneas**: > 90%
- **Cobertura de branches**: > 85%
- **Cobertura de mutación**: > 80%

### Tools de Calidad
```kotlin
// build.gradle.kts
plugins {
    id("jacoco")
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
}

jacoco {
    toolVersion = "0.8.8"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
```

## Documentación Viva

Cada feature implementada debe incluir:
1. **BDD Specs** como documentación ejecutable
2. **Ejemplos de uso** en código
3. **ADRs** para decisiones arquitecturales importantes
4. **Changelog** actualizado con conventional commits

---

**Este flujo es OBLIGATORIO para todo desarrollo en el proyecto.**