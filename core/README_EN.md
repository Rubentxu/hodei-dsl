# Core Module (Hodei DSL)

The core module contains the domain model, typed DSL, and fundamental execution components of the Hodei Pipeline DSL system. It is the heart that defines how a pipeline is described (model and builders) and how it is evaluated and executed (context, executors, fault tolerance, metrics, and logging).

Its design follows hexagonal architecture principles: the domain is pure and stable; details (CLI, external execution, plugins) are integrated through clear layers and ports.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Domain Model](#domain-model)
- [Step Handler System](#step-handler-system)
- [Execution Flow](#execution-flow)
- [DSL (Typed Builders)](#dsl-typed-builders)
- [Step Extension Mechanisms](#step-extension-mechanisms)
- [Pipeline Execution](#pipeline-execution)
- [Testing](#testing)
- [Integration with Other Modules](#integration-with-other-modules)

## Overview

- **Immutable and explicit domain**: `Pipeline`, `Stage`, `Step`, `Agent`, `PostAction`, `WhenCondition` with validations and helpers (`withStatus`, `withEnvironment`, etc.).
- **Kotlin DSL**, compile-safe, for defining pipelines with blocks: `pipeline { stage("Build") { steps { sh("gradle build") } } }`.
- **Execution engine**: `PipelineExecutor`, `StageExecutor`, `StepExecutor`, `ExecutionContext*`, `FaultTolerance*`, `PipelineLogger`, `PipelineMetrics`, `ResourcePool`, `StashStorage`.
- **Conceptual compatibility** with Jenkins Declarative Pipeline (stages, steps, agent, environment, when, post, parallel, retry, timeout, archive/junit, stash/unstash).
- **SOLID Architecture**: Refactored step execution system following SOLID principles with Strategy Pattern handlers.

## Architecture

### Hexagonal Architecture Overview

```mermaid
flowchart LR
  subgraph Domain
    A[Pipeline] --> B[Stage]
    B --> C[Step]
    A --> D[Agent]
    B --> E[WhenCondition]
    B --> F[PostAction]
  end

  subgraph DSL
    G[PipelineBuilder] -->|builds| A
    H[StageBuilder] -->|builds| B
    I[StepsBuilder] -->|builds| C
    J[WhenBuilder] -->|builds| E
    K[AgentBuilder] -->|builds| D
    L[PostBuilder] -->|builds| F
  end

  subgraph Execution
    M[ExecutionContext*]
    N[PipelineExecutor]
    O[StageExecutor]
    P[StepExecutor]
    Q[StepHandlerRegistry]
    R[StepHandler]
    S[FaultTolerance]
    T[Logger/Metrics]
    U[ResourcePool]
    V[StashStorage]
  end

  G -. uses .-> H
  H -. uses .-> I
  N --> O --> P
  P --> Q --> R
  P -. uses .-> S
  N -. emits .-> T
  P -. accesses .-> V
  N -. coordinates .-> U
  N -. reads .-> M
```

### New SOLID Step Handler Architecture

```mermaid
classDiagram
  class StepHandler~T~ {
    <<interface>>
    +validate(step: T, context: ExecutionContext): List~ValidationError~
    +prepare(step: T, context: ExecutionContext)
    +execute(step: T, context: ExecutionContext): StepResult
    +cleanup(step: T, context: ExecutionContext, result: StepResult)
  }
  
  class AbstractStepHandler~T~ {
    <<abstract>>
    +executeWithLifecycle(step: T, context: ExecutionContext): StepResult
    #getStepName(step: T): String
    -enhanceResult(result: StepResult): StepResult
    -createValidationFailureResult(): StepResult
    -createTimeoutResult(): StepResult
    -createFailureResult(): StepResult
  }
  
  class StepHandlerRegistry {
    <<object>>
    -handlers: ConcurrentHashMap
    +register~T~(stepClass: KClass~T~, handler: StepHandler~T~)
    +getHandler~T~(stepClass: KClass~T~): StepHandler~T~
    +hasHandler(stepClass: KClass): Boolean
    +unregister(stepClass: KClass)
    +clear()
  }
  
  class EchoStepHandler {
    +validate(step: Step.Echo, context: ExecutionContext): List~ValidationError~
    +execute(step: Step.Echo, context: ExecutionContext): StepResult
    +getStepName(step: Step.Echo): String
  }
  
  class ShellStepHandler {
    +validate(step: Step.Shell, context: ExecutionContext): List~ValidationError~
    +execute(step: Step.Shell, context: ExecutionContext): StepResult
    +getStepName(step: Step.Shell): String
  }
  
  class StepExecutor {
    -config: StepExecutorConfig
    -stashStorage: StashStorage
    -workloadAnalyzer: WorkloadAnalyzer
    +execute(step: Step, context: ExecutionContext): StepResult
    -executeStepInternal(step: Step, context: ExecutionContext): StepResult
  }
  
  StepHandler <|-- AbstractStepHandler
  AbstractStepHandler <|-- EchoStepHandler
  AbstractStepHandler <|-- ShellStepHandler
  StepExecutor --> StepHandlerRegistry : uses
  StepHandlerRegistry --> StepHandler : manages
```

**Key Benefits:**
- **Single Responsibility**: Each handler focuses on one step type
- **Open/Closed**: New step types can be added without modifying existing code
- **Liskov Substitution**: All handlers implement the same interface
- **Interface Segregation**: Clean, focused interfaces
- **Dependency Inversion**: StepExecutor depends on abstractions

## Domain Model

Main classes are in `dev.rubentxu.hodei.core.domain.model`:

```mermaid
classDiagram
  class Pipeline {
    +id: String
    +stages: List~Stage~
    +globalEnvironment: Map~String,String~
    +status: PipelineStatus
    +agent: Agent?
    +metadata: Map~String,Any~
    +withStatus(newStatus): Pipeline
    +withMetadata(key,value): Pipeline
    <<immutable>>
  }
  class Stage {
    +name: String
    +steps: List~Step~
    +agent: Agent?
    +environment: Map~String,String~
    +whenCondition: WhenCondition?
    +post: List~PostAction~
    +shouldExecute(context): Boolean
    +withEnvironment(env): Stage
    +withAgent(agent): Stage
    <<immutable>>
  }
  class Step {
    <<sealed>>
  }
  class Agent { <<sealed>> }
  class PostAction { <<sealed>> }
  class WhenCondition { <<sealed>> }
  class PipelineStatus {
    <<enumeration>>
    PENDING
    RUNNING
    SUCCESS
    FAILURE
    CANCELLED
    PAUSED
  }
  
  Pipeline --> Stage
  Stage --> Step
  Pipeline --> Agent
  Stage --> Agent
  Stage --> WhenCondition
  Stage --> PostAction
```

**Supported Step types** (see `Step.kt`): `Shell`, `Echo`, `Dir`, `WithEnv`, `Parallel`, `Retry`, `Timeout`, `ArchiveArtifacts`, `PublishTestResults`, `Stash`, `Unstash`.

**Supported Agents**: `Any`, `None`, `Label`, `Docker(image, args, volumes, environment)`, `Kubernetes(yaml, namespace)`.

**WhenConditions**: `Branch`, `Environment`, `Predicate` (functional), `ChangeSet`, combinators `And/Or/Not`.

## Step Handler System

The new step handler system implements the Strategy Pattern for step execution, following SOLID principles.

### Handler Lifecycle Flow

```mermaid
sequenceDiagram
  participant SE as StepExecutor
  participant SR as StepHandlerRegistry
  participant SH as StepHandler
  participant AH as AbstractStepHandler
  participant CH as ConcreteHandler

  SE->>SE: execute(step, context)
  SE->>SR: hasHandler(step::class)
  SR-->>SE: true
  SE->>SR: getHandler(step)
  SR-->>SE: handler
  SE->>AH: executeWithLifecycle(step, context, config)
  
  AH->>CH: validate(step, context)
  CH-->>AH: List<ValidationError>
  
  alt No validation errors
    AH->>CH: prepare(step, context)
    CH-->>AH: void
    
    alt With timeout
      AH->>AH: withTimeout(timeout) {
      AH->>CH: execute(step, context)
      CH-->>AH: StepResult
      AH->>AH: }
    else No timeout
      AH->>CH: execute(step, context)
      CH-->>AH: StepResult
    end
    
    AH->>CH: cleanup(step, context, result)
    CH-->>AH: void
    AH->>AH: enhanceResult(result, startTime, context)
    AH-->>SE: Enhanced StepResult
  else Validation errors
    AH->>AH: createValidationFailureResult(stepName, errors, startTime)
    AH-->>SE: Validation failure StepResult
  end
```

### Currently Implemented Handlers

#### FASE 1: Infrastructure ✅
- `StepHandler<T>` interface
- `StepHandlerRegistry` object
- `AbstractStepHandler` base class
- Integration with `StepExecutor`

#### FASE 2: Simple Handlers ✅
- `EchoStepHandler` - handles `Step.Echo`
- `ShellStepHandler` - handles `Step.Shell`
- `ArchiveArtifactsStepHandler` - handles `Step.ArchiveArtifacts`
- `PublishTestResultsStepHandler` - handles `Step.PublishTestResults`
- `StashStepHandler` - handles `Step.Stash`
- `UnstashStepHandler` - handles `Step.Unstash`

#### FASE 3: Complex Handlers (Pending)
- `DirStepHandler` - for `Step.Dir`
- `WithEnvStepHandler` - for `Step.WithEnv`
- `ParallelStepHandler` - for `Step.Parallel`
- `RetryStepHandler` - for `Step.Retry`
- `TimeoutStepHandler` - for `Step.Timeout`

### Handler Registration

```mermaid
flowchart LR
  A[DefaultHandlerRegistration] --> B[registerDefaultHandlers()]
  B --> C[StepHandlerRegistry.register]
  C --> D[EchoStepHandler]
  C --> E[ShellStepHandler]
  C --> F[ArchiveArtifactsStepHandler]
  C --> G[PublishTestResultsStepHandler]
  C --> H[StashStepHandler]
  C --> I[UnstashStepHandler]
```

## Execution Flow

### Complete Pipeline Execution Flow

```mermaid
sequenceDiagram
  participant Client
  participant PE as PipelineExecutor
  participant SE as StageExecutor
  participant STE as StepExecutor
  participant SHR as StepHandlerRegistry
  participant SH as StepHandler
  participant CTX as ExecutionContext
  participant BUS as PipelineEventBus

  Client->>PE: execute(pipeline, context)
  PE->>CTX: prepare execution context
  PE->>BUS: publish(PipelineStarted)
  
  loop for each stage in pipeline
    PE->>SE: execute(stage, context)
    
    alt stage.whenCondition evaluates to true
      SE->>BUS: publish(StageStarted)
      SE->>CTX: apply stage environment/agent
      
      loop for each step in stage
        SE->>STE: execute(step, context)
        
        alt handler exists for step type
          STE->>SHR: hasHandler(step::class)
          SHR-->>STE: true
          STE->>SHR: getHandler(step)
          SHR-->>STE: handler
          STE->>SH: executeWithLifecycle(step, context, config)
          SH-->>STE: StepResult
        else fallback to legacy execution
          STE->>STE: executeStepInternal(step, context)
          STE-->>STE: StepResult
        end
        
        STE-->>SE: StepResult
      end
      
      SE->>SE: execute post actions
      SE-->>PE: StageResult
      PE->>BUS: publish(StageFinished)
    else stage skipped due to when condition
      SE-->>PE: StageResult(SKIPPED)
      PE->>BUS: publish(StageSkipped)
    end
  end
  
  PE->>BUS: publish(PipelineFinished)
  PE-->>Client: PipelineResult
```

### Step Execution Decision Flow

```mermaid
flowchart TD
  A[Receive Step] --> B{Has Handler?}
  B -->|Yes| C[Use StepHandler]
  B -->|No| D[Use Legacy Implementation]
  
  C --> E[Handler Lifecycle]
  E --> F[Validate]
  F --> G{Valid?}
  G -->|No| H[Return Validation Error]
  G -->|Yes| I[Prepare]
  I --> J[Execute]
  J --> K[Cleanup]
  K --> L[Enhance Result]
  L --> M[Return StepResult]
  
  D --> N[Legacy when/switch]
  N --> O{Step Type}
  O -->|Shell| P[Execute Shell]
  O -->|Echo| Q[Execute Echo]
  O -->|Dir| R[Execute Dir]
  O -->|Other| S[Execute Other]
  P --> M
  Q --> M
  R --> M
  S --> M
  
  H --> M
```

## DSL (Typed Builders)

Package: `dev.rubentxu.hodei.core.dsl.builders`.

- `PipelineBuilder`: defines `agent`, `environment`, `stage`, `post` and builds `Pipeline`.
- `StageBuilder`: defines agent per stage, variables, `when`, `steps`, `post`. Includes shortcut `sh("cmd")` for simple stages.
- `StepsBuilder`: provides step operations: `sh`, `echo`, `dir {}`, `withEnv(List<String>) {}`, `parallel {}`, `retry {}`, `timeout {}`, `archiveArtifacts`, `publishTestResults`, `stash`, `unstash`, `emailext` (helper).
- `WhenBuilder`, `AgentBuilder`, `EnvironmentBuilder`, `PostBuilder`: specialized sub-builders.

### Basic Example

```kotlin
val pipeline = pipeline {
  agent { any() }
  environment {
    set("JAVA_HOME", "/opt/jdk")
  }
  stage("Build") {
    steps {
      sh("./gradlew build")
      archiveArtifacts("build/libs/*.jar", fingerprint = true)
    }
  }
  stage("Test") {
    steps {
      sh("./gradlew test")
      publishTestResults("build/test-results/test/*.xml")
    }
  }
}
```

### DSL Construction Flow

```mermaid
sequenceDiagram
  participant U as User (Kotlin DSL)
  participant PB as PipelineBuilder
  participant SB as StageBuilder
  participant StB as StepsBuilder
  U->>PB: pipeline { ... }
  PB->>SB: stage("Build") { ... }
  SB->>StB: steps { sh("gradle build") }
  StB-->>SB: List<Step>
  SB-->>PB: Stage
  PB-->>U: Pipeline (immutable)
```

## Step Extension Mechanisms

### Extension by Plugins

Two clearly defined parts:
- **Registration mechanism** (third parties): implements StepExtension with a typed scope and registers via ServiceLoader (META-INF/services) or programmatically for tests.
- **Typed DSL generation** (core): extension functions over StepsBuilder that create the scope and delegate to StepExtensionRegistry.

Contract (core):
```kotlin
interface StepExtension {
  val name: String
  fun createScope(): Any
  fun execute(scope: Any, stepsBuilder: StepsBuilder)
}

object StepExtensionRegistry {
  fun get(name: String): StepExtension?
  fun register(extension: StepExtension)
  fun unregister(name: String)
}

// Support in StepsBuilder for use by generated code
fun StepsBuilder.invokeExtension(name: String, scope: Any)
```

Example generated function:
```kotlin
// In plugin-slack (generated):
class SlackStepScope { var message: String = ""; var channel: String = "#general" }

fun StepsBuilder.slack(block: SlackStepScope.() -> Unit) {
  val scope = SlackStepScope().apply(block)
  invokeExtension("slack", scope)
}
```

### Extension Patterns

1. **DSL Sugar via extension functions** (without creating new Step types)
2. **New Step types** (deep integration)
3. **Plugins** (extension outside core)

## Pipeline Execution

The execution engine in `dev.rubentxu.hodei.core.execution` orchestrates concurrent and fault-tolerant execution.

### Main Components

- `ExecutionContext`, `ExecutionContextFactory`, `ExecutionContextBuilder`: encapsulate environment, variables, workspace and utilities for execution.
- `PipelineExecutor`: coordinates stages, applies global timeouts and publishes events.
- `StageExecutor`: evaluates `whenCondition`, applies `agent/environment` and executes stage steps.
- `StepExecutor`: executes each `Step` with validations, dispatcher selection (CPU/IO), timeouts, retries, parallelism, stash/unstash, junit/artifacts, etc.
- `FaultTolerance` and `FaultToleranceConfig`: backoff, retries and graceful degradation.
- `PipelineLogger` and `ConfigurablePipelineLogger`: structured logging by levels.
- `PipelineMetrics`: latencies, counters and states per stage/step.
- `ResourcePool`: concurrency control and permits.
- `StashStorage` (e.g., `FileSystemStashStorage`): temporary artifact persistence between stages.

### Execution Sequence Diagram

```mermaid
sequenceDiagram
  participant PE as PipelineExecutor
  participant SE as StageExecutor
  participant STE as StepExecutor
  participant CTX as ExecutionContext
  participant BUS as PipelineEventBus

  PE->>CTX: create/get context
  PE->>BUS: publish(PipelineStarted)
  loop stages in order
    PE->>SE: execute(stage, CTX)
    alt stage.whenCondition == true
      SE->>STE: execute(step1, CTX)
      STE-->>SE: StepResult
      SE->>STE: execute(stepN, CTX)
      STE-->>SE: StepResult
      SE-->>PE: StageResult
      PE->>BUS: publish(StageFinished)
    else skipped by condition
      SE-->>PE: StageSkipped
      PE->>BUS: publish(StageSkipped)
    end
  end
  PE->>BUS: publish(PipelineFinished)
  PE-->>Caller: PipelineResult
```

### Fault Tolerance and Degradation

```mermaid
flowchart TD
  A[Receive Step] --> B{Validation}
  B -- ok --> C[Select dispatcher IO/CPU]
  C --> D{Step Type}
  D -->|Shell| E[Execute command]
  D -->|Parallel| F[Launch branches]
  D -->|Retry| G[Apply policy]
  D -->|Timeout| H[Limit duration]
  D -->|Archive/JUnit| I[Post-process]
  D -->|Stash/Unstash| J[Temporal persistence]
  E --> K{Success?}
  F --> K
  G --> K
  H --> K
  I --> K
  J --> K
  K -- yes --> L[Emit metrics and logs]
  K -- no --> M[Build error result]
```

## Testing

### Framework and Structure

- **Framework**: Kotest 5 + JUnit Platform, with tests in `core/src/test/kotlin`.
- **Test Categories**:
  - Domain tests: `PipelineModelSpec`, `WhenConditionSpec`
  - DSL tests: `DSLBuilderSpec`
  - Execution tests: `ExecutionContextSpec`, `StashSystemSpec`
  - Handler tests: `EchoStepHandlerSpec`, `HandlerRegistrationSpec`
  - Integration tests: `PipelineDSLGradleBuildSpec`

### Handler Testing Strategy

```mermaid
flowchart LR
  A[Unit Tests] --> B[Handler Validation]
  A --> C[Handler Execution]
  A --> D[Handler Lifecycle]
  
  E[Integration Tests] --> F[Handler Registration]
  E --> G[StepExecutor Integration]
  E --> H[End-to-End Pipeline]
  
  I[Test Examples] --> J[EchoStepHandlerSpec]
  I --> K[HandlerRegistrationSpec]
  I --> L[SimpleStepHandlersIntegrationSpec]
```

### Running Tests

```bash
# Core module only
gradle :core:test

# Specific test
gradle :core:test --tests "dev.rubentxu.hodei.core.execution.handlers.EchoStepHandlerSpec"

# Handler tests only
gradle :core:test --tests "*Handler*"
```

## Integration with Other Modules

- `:execution`: contains additional execution implementations and tests; shares concepts with core but focused on runtime scenarios.
- `:compiler`: integration with scripting and Tooling API to compile pipelines `.kts` and resolve dependencies.
- `:steps` and `:plugins`: extension of the ecosystem of steps and plugins.
- `:cli` and `:library`: packaging and programmatic/CLI access.

### Module Dependencies

The core module maintains minimal dependencies to preserve domain purity:
- Kotlin stdlib and coroutines
- kotlinx-serialization for JSON support (where applicable)

### Cross-Module Interaction

```mermaid
flowchart LR
  A[Core] --> B[Execution]
  A --> C[Compiler]
  A --> D[Steps]
  A --> E[Plugins]
  A --> F[CLI]
  A --> G[Library]
  
  B --> A
  C --> A
  D --> A
  E --> A
  F --> A
  G --> A
```

## References

- Repository documentation in `docs/`:
  - `docs/architecture.md` – global architecture vision
  - `docs/execution-model.md` – execution model detail
  - `docs/dsl-specification.md` – DSL specification
  - `docs/plugin-system.md` – plugin system
  - `docs/examples/*` – practical examples

## Contributing

When contributing to the core module:

1. **Domain changes**: Ensure immutability and add appropriate tests
2. **DSL changes**: Maintain type safety and add builder tests
3. **Execution changes**: Follow SOLID principles and add handler tests
4. **New step types**: Consider handler implementation following the established pattern

## Changelog (Core Capabilities Summary)

- ✅ Immutable domain with Pipeline/Stage/Step and When/Post/Agent
- ✅ Typed DSL with DslMarkers for safe composition
- ✅ SOLID step handler system with Strategy Pattern
- ✅ Executors with support for parallel/retry/timeout, stash, artifacts and junit
- ✅ Configurable metrics, events and logger
- ✅ ResourcePool and fault tolerance with backoff
- ✅ Simple step handlers: Echo, Shell, ArchiveArtifacts, PublishTestResults, Stash, Unstash
- ⏳ Complex step handlers: Dir, WithEnv, Parallel, Retry, Timeout (FASE 3)
- ⏳ Context receivers modernization (FASE 4)
- ⏳ Legacy code cleanup (FASE 5)