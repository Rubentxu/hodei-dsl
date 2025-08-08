# Especificaciones de la API Core - Hodei Pipeline DSL

## Resumen Ejecutivo

La API Core de Hodei Pipeline DSL define las interfaces fundamentales y contratos que permiten la creación, compilación y ejecución de pipelines de CI/CD con **type-safety completa** y **compatibilidad total con Jenkins**.

## Principios de Diseño de la API

### 🎯 Type-Safety First
- **Compile-time validation**: Errores detectados durante compilación
- **IDE Support**: Autocompletado y navegación completa
- **Refactoring Safe**: Cambios seguros con herramientas de refactor

### 🔄 Jenkins API Compatibility  
- **Sintaxis idéntica**: Mismos nombres de métodos y parámetros
- **Comportamiento equivalente**: Resultados consistentes con Jenkins
- **Migration Path**: Migración sin cambios de código

### ⚡ Performance Oriented
- **Lazy evaluation**: Construcción diferida del pipeline
- **Coroutine-based**: Ejecución paralela eficiente
- **Memory efficient**: Uso optimizado de recursos

## Core Interfaces

### 1. Pipeline DSL Core

#### Pipeline Builder Interface
```kotlin
@DslMarker
annotation class PipelineDSL

/**
 * Entry point del DSL. Define un pipeline de CI/CD con stages y steps.
 * 
 * Ejemplo:
 * ```kotlin
 * pipeline {
 *     stage("Build") {
 *         steps {
 *             sh("./gradlew build")
 *         }
 *     }
 * }
 * ```
 */
@PipelineDSL
interface Pipeline {
    
    /**
     * Contexto de ejecución del pipeline
     */
    val context: ExecutionContext
    
    /**
     * Container de extensiones de plugins
     */
    val extensions: ExtensionContainer
    
    /**
     * Define un stage secuencial
     * @param name Nombre del stage
     * @param block Constructor del stage
     */
    fun stage(name: String, block: StageBuilder.() -> Unit)
    
    /**
     * Define ejecución paralela de múltiples branches
     * @param branches Constructor de branches paralelos
     */
    fun parallel(branches: ParallelBuilder.() -> Unit)
    
    /**
     * Configura variables de entorno globales
     * @param block Constructor de environment
     */
    fun environment(block: EnvironmentBuilder.() -> Unit)
    
    /**
     * Define agente global para todo el pipeline
     * @param block Constructor de agent
     */
    fun agent(block: AgentBuilder.() -> Unit)
    
    /**
     * Configura acciones post-ejecución
     * @param block Constructor de post actions
     */
    fun post(block: PostBuilder.() -> Unit)
    
    /**
     * Ejecuta el pipeline completo
     * @return Resultado de la ejecución
     */
    suspend fun execute(): PipelineResult
}
```

#### Stage Builder Interface
```kotlin
/**
 * Constructor de stages individuales con steps y configuración
 */
@PipelineDSL
interface StageBuilder {
    
    /**
     * Nombre del stage
     */
    var name: String
    
    /**
     * Define steps a ejecutar en este stage
     * @param block Constructor de steps
     */
    fun steps(block: StepsBuilder.() -> Unit)
    
    /**
     * Define agente específico para este stage
     * @param block Constructor de agent
     */
    fun agent(block: AgentBuilder.() -> Unit)
    
    /**
     * Configura variables de entorno para este stage
     * @param block Constructor de environment
     */
    fun environment(block: EnvironmentBuilder.() -> Unit)
    
    /**
     * Configura condiciones para ejecutar este stage
     * @param condition Condición como expresión string
     */
    fun `when`(condition: String)
    
    /**
     * Configura condiciones para ejecutar este stage
     * @param block Constructor de condiciones
     */
    fun `when`(block: WhenBuilder.() -> Unit)
    
    /**
     * Configura acciones post-ejecución del stage
     * @param block Constructor de post actions
     */
    fun post(block: PostBuilder.() -> Unit)
    
    /**
     * Construye la instancia final del stage
     * @return Stage configurado
     */
    internal fun build(): Stage
}
```

#### Steps Builder Interface  
```kotlin
/**
 * Constructor de steps compatible 100% con Jenkins Pipeline
 */
@PipelineDSL
interface StepsBuilder {
    
    // === BASIC STEPS ===
    
    /**
     * Ejecuta comando shell
     * @param script Comando a ejecutar
     * @param returnStdout Si debe retornar la salida como string
     * @param returnStatus Si debe retornar el exit code
     * @param encoding Encoding para la salida (default: UTF-8)
     * @return Output del comando si returnStdout=true
     */
    fun sh(
        script: String,
        returnStdout: Boolean = false,
        returnStatus: Boolean = false,
        encoding: String = "UTF-8"
    ): String?
    
    /**
     * Imprime mensaje en el log
     * @param message Mensaje a imprimir
     */
    fun echo(message: String)
    
    /**
     * Ejecuta steps en directorio específico
     * @param path Path relativo o absoluto
     * @param block Steps a ejecutar en el directorio
     */
    fun dir(path: String, block: StepsBuilder.() -> Unit)
    
    /**
     * Ejecuta steps con variables de entorno adicionales
     * @param env Lista de variables en formato "KEY=value"
     * @param block Steps a ejecutar con el environment
     */
    fun withEnv(env: List<String>, block: StepsBuilder.() -> Unit)
    
    // === CONTROL FLOW ===
    
    /**
     * Ejecuta steps en paralelo
     * @param branches Map de nombre -> block de steps
     */
    fun parallel(branches: Map<String, StepsBuilder.() -> Unit>)
    
    /**
     * Reintenta steps en caso de fallo
     * @param times Número de intentos
     * @param block Steps a reintentar
     */
    fun retry(times: Int, block: StepsBuilder.() -> Unit)
    
    /**
     * Ejecuta steps con timeout
     * @param time Tiempo límite
     * @param unit Unidad de tiempo
     * @param block Steps a ejecutar con timeout
     */
    fun timeout(time: Int, unit: TimeUnit, block: StepsBuilder.() -> Unit)
    
    /**
     * Captura y maneja errores
     * @param block Steps que pueden fallar
     * @param catchBlock Steps a ejecutar en caso de error
     * @param finallyBlock Steps a ejecutar siempre
     */
    fun script(
        block: StepsBuilder.() -> Unit,
        catchBlock: (StepsBuilder.(Exception) -> Unit)? = null,
        finallyBlock: (StepsBuilder.() -> Unit)? = null
    )
    
    // === SCM & ARCHIVING ===
    
    /**
     * Checkout código desde SCM
     * @param scm Configuración de SCM
     */
    fun checkout(scm: SCMConfig)
    
    /**
     * Archiva artefactos
     * @param artifacts Patrón de archivos a archivar
     * @param allowEmptyArchive Permitir archive vacío
     * @param fingerprint Generar fingerprint de archivos
     */
    fun archiveArtifacts(
        artifacts: String,
        allowEmptyArchive: Boolean = false,
        fingerprint: Boolean = false
    )
    
    /**
     * Publica resultados de tests JUnit
     * @param testResults Patrón de archivos XML de tests
     * @param allowEmptyResults Permitir resultados vacíos
     * @param keepLongStdio Mantener salida larga en logs
     */
    fun junit(
        testResults: String,
        allowEmptyResults: Boolean = false,
        keepLongStdio: Boolean = false
    )
    
    // === BUILD TOOLS ===
    
    /**
     * Ejecuta build con herramienta específica
     * @param tool Herramienta (gradle, maven, npm, etc.)
     * @param targets Targets/tasks a ejecutar
     * @param options Opciones adicionales
     */
    fun build(tool: BuildTool, targets: List<String>, options: Map<String, Any> = emptyMap())
    
    /**
     * Publica artefactos a repositorio
     * @param repository Configuración del repositorio
     * @param artifacts Artefactos a publicar
     */
    fun publishArtifacts(repository: RepositoryConfig, artifacts: List<String>)
    
    // === NOTIFICATIONS ===
    
    /**
     * Envía notificación por email
     * @param to Destinatarios
     * @param subject Asunto
     * @param body Cuerpo del mensaje
     * @param attachments Archivos adjuntos
     */
    fun emailext(
        to: String,
        subject: String,
        body: String,
        attachments: String = ""
    )
    
    /**
     * Envía input/prompt al usuario
     * @param message Mensaje del prompt
     * @param id ID del input
     * @param ok Texto del botón OK
     * @param submitter Usuarios que pueden responder
     * @return Respuesta del usuario
     */
    suspend fun input(
        message: String,
        id: String? = null,
        ok: String = "Proceed",
        submitter: String? = null
    ): String
    
    // === EXTENSIBILITY ===
    
    /**
     * Ejecuta step customizado
     * @param stepName Nombre del step (de plugin o custom)
     * @param parameters Parámetros del step
     */
    fun step(stepName: String, parameters: Map<String, Any> = emptyMap())
    
    /**
     * Aplica wrapper script alrededor de steps
     * @param wrapper Configuración del wrapper
     * @param block Steps a envolver
     */
    fun wrap(wrapper: WrapperConfig, block: StepsBuilder.() -> Unit)
}
```

### 2. Execution Context

#### ExecutionContext Interface
```kotlin
/**
 * Contexto de ejecución que se pasa a todos los steps
 * Contiene información del entorno, directorios, variables, etc.
 */
interface ExecutionContext {
    
    /**
     * Directorio de trabajo actual
     */
    val workDir: Path
    
    /**
     * Variables de entorno disponibles
     */
    val environment: Map<String, String>
    
    /**
     * Logger para output del pipeline
     */
    val logger: PipelineLogger
    
    /**
     * ID único de esta ejecución
     */
    val executionId: String
    
    /**
     * ID del build/job
     */
    val buildId: String
    
    /**
     * Información del workspace
     */
    val workspace: WorkspaceInfo
    
    /**
     * Información del job/pipeline
     */
    val jobInfo: JobInfo
    
    /**
     * Directorio para artefactos
     */
    val artifactDir: Path
    
    /**
     * Launcher para ejecutar comandos
     */
    val launcher: CommandLauncher
    
    /**
     * Métricas del pipeline
     */
    val metrics: PipelineMetrics
    
    /**
     * Crea un contexto derivado con modificaciones
     */
    fun copy(
        workDir: Path = this.workDir,
        environment: Map<String, String> = this.environment,
        launcher: CommandLauncher = this.launcher
    ): ExecutionContext
    
    companion object {
        /**
         * Crea contexto por defecto para testing/desarrollo
         */
        fun default(): ExecutionContext
    }
}
```

#### PipelineLogger Interface
```kotlin
/**
 * Logger especializado para pipelines con soporte para
 * diferentes niveles y output estructurado
 */
interface PipelineLogger {
    
    /**
     * Log de información general
     */
    fun info(message: String, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log de warnings
     */
    fun warn(message: String, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log de errores
     */
    fun error(message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log de debug (solo en modo debug)
     */
    fun debug(message: String, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Output directo del comando (stdout)
     */
    fun stdout(output: String)
    
    /**
     * Error output del comando (stderr) 
     */
    fun stderr(output: String)
    
    /**
     * Inicia una sección colapsible en el log
     */
    fun startSection(name: String)
    
    /**
     * Finaliza la sección actual
     */
    fun endSection()
    
    /**
     * Log con timestamp personalizado
     */
    fun logWithTimestamp(level: LogLevel, message: String, timestamp: Instant = Instant.now())
}
```

### 3. Step Execution Model

#### PipelineStep Interface
```kotlin
/**
 * Interface base para todos los steps del pipeline
 * Define el contrato de ejecución uniforme
 */
interface PipelineStep {
    
    /**
     * Nombre descriptivo del step
     */
    val name: String
    
    /**
     * Tipo de workload para optimizar dispatcher
     */
    val workloadType: WorkloadType
        get() = WorkloadType.DEFAULT
    
    /**
     * Timeout por defecto para este step
     */
    val defaultTimeout: Duration?
        get() = null
    
    /**
     * Ejecuta el step en el contexto dado
     * @param context Contexto de ejecución
     * @return Resultado de la ejecución
     */
    suspend fun execute(context: ExecutionContext): StepResult
    
    /**
     * Validación previa a la ejecución
     * @param context Contexto de ejecución
     * @return Lista de errores de validación
     */
    fun validate(context: ExecutionContext): List<ValidationError> = emptyList()
    
    /**
     * Preparación previa a la ejecución
     * @param context Contexto de ejecución
     */
    suspend fun prepare(context: ExecutionContext) {}
    
    /**
     * Limpieza posterior a la ejecución
     * @param context Contexto de ejecución
     * @param result Resultado de la ejecución
     */
    suspend fun cleanup(context: ExecutionContext, result: StepResult) {}
}
```

#### StepResult Sealed Class
```kotlin
/**
 * Resultado de ejecución de un step
 */
sealed class StepResult {
    
    /**
     * Ejecución exitosa sin valor de retorno
     */
    object Success : StepResult()
    
    /**
     * Ejecución exitosa con valor de retorno
     * @param value Valor retornado por el step
     */
    data class Value<T>(val value: T) : StepResult()
    
    /**
     * Ejecución fallida
     * @param message Mensaje de error
     * @param cause Excepción causante (opcional)
     * @param recoverable Si el error es recuperable
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null,
        val recoverable: Boolean = false
    ) : StepResult()
    
    /**
     * Ejecución cancelada
     * @param reason Razón de la cancelación
     */
    data class Cancelled(val reason: String) : StepResult()
    
    /**
     * Ejecución pausada (para input/approval steps)
     * @param message Mensaje para el usuario
     * @param resumeToken Token para reanudar
     */
    data class Paused(
        val message: String,
        val resumeToken: String
    ) : StepResult()
    
    /**
     * Helpers para verificar estado
     */
    val isSuccess: Boolean
        get() = this is Success || this is Value<*>
    
    val isFailure: Boolean
        get() = this is Failure
        
    val isCancelled: Boolean
        get() = this is Cancelled
        
    val isPaused: Boolean
        get() = this is Paused
}
```

### 4. Agent System

#### Agent Interface
```kotlin
/**
 * Agente de ejecución que define dónde y cómo se ejecutan los steps
 */
interface Agent {
    
    /**
     * Tipo de agente
     */
    val type: AgentType
    
    /**
     * Identificador único del agente
     */
    val id: String
    
    /**
     * Prepara el entorno de ejecución
     * @param context Contexto base
     * @return Contexto modificado para este agente
     */
    suspend fun prepare(context: ExecutionContext): ExecutionContext
    
    /**
     * Limpia el entorno después de la ejecución
     * @param context Contexto de ejecución
     */
    suspend fun cleanup(context: ExecutionContext)
    
    /**
     * Verifica si el agente está disponible
     * @return true si está disponible
     */
    suspend fun isAvailable(): Boolean
}

/**
 * Agente que ejecuta en el host local
 */
class LocalAgent : Agent {
    override val type = AgentType.LOCAL
    override val id = "local"
    
    override suspend fun prepare(context: ExecutionContext): ExecutionContext = context
    override suspend fun cleanup(context: ExecutionContext) {}
    override suspend fun isAvailable() = true
}

/**
 * Agente que ejecuta dentro de contenedor Docker
 */
class DockerAgent(
    private val image: String,
    private val args: String = "",
    private val volumes: Map<String, String> = emptyMap(),
    private val environment: Map<String, String> = emptyMap()
) : Agent {
    
    override val type = AgentType.DOCKER
    override val id = "docker-$image"
    
    override suspend fun prepare(context: ExecutionContext): ExecutionContext {
        // Crear y configurar contenedor
        val containerId = createContainer(image, args, volumes, environment)
        return context.copy(
            launcher = DockerCommandLauncher(containerId)
        )
    }
    
    override suspend fun cleanup(context: ExecutionContext) {
        // Limpiar contenedor
        if (context.launcher is DockerCommandLauncher) {
            context.launcher.stopAndRemove()
        }
    }
    
    override suspend fun isAvailable(): Boolean {
        // Verificar si Docker está disponible y la imagen existe
        return DockerUtils.isAvailable() && DockerUtils.imageExists(image)
    }
}
```

### 5. Plugin Extension System

#### Plugin Interface
```kotlin
/**
 * Interface base para plugins del sistema
 */
interface PipelinePlugin<T : PluginAware> {
    
    /**
     * ID único del plugin
     */
    val id: String
    
    /**
     * Versión del plugin
     */
    val version: String
    
    /**
     * Dependencias de otros plugins
     */
    val dependencies: List<String>
        get() = emptyList()
    
    /**
     * Aplica el plugin al target
     * @param target Objeto al que aplicar el plugin
     */
    fun apply(target: T)
    
    /**
     * Schema de extensiones que provee este plugin
     * @return Schema para generación de DSL
     */
    fun getExtensionSchema(): ExtensionSchema
    
    /**
     * Inicialización del plugin
     */
    suspend fun initialize() {}
    
    /**
     * Limpieza del plugin
     */
    suspend fun destroy() {}
}

/**
 * Marker interface para objetos que pueden recibir plugins
 */
interface PluginAware {
    val extensions: ExtensionContainer
}
```

#### ExtensionContainer Interface
```kotlin
/**
 * Container para extensiones de plugins con type-safety
 */
interface ExtensionContainer {
    
    /**
     * Crea una nueva extensión
     * @param name Nombre de la extensión
     * @param factory Factory para crear la instancia
     * @return Instancia de la extensión
     */
    fun <T : Any> create(name: String, factory: () -> T): T
    
    /**
     * Obtiene extensión existente
     * @param name Nombre de la extensión
     * @return Instancia de la extensión o null
     */
    fun <T : Any> findByName(name: String): T?
    
    /**
     * Obtiene extensión por tipo
     * @return Instancia de la extensión o null
     */
    inline fun <reified T : Any> findByType(): T?
    
    /**
     * Configura extensión existente
     * @param name Nombre de la extensión
     * @param configure Block de configuración
     */
    fun <T : Any> configure(name: String, configure: T.() -> Unit)
    
    /**
     * Lista todas las extensiones registradas
     * @return Map de nombre -> extensión
     */
    fun getAll(): Map<String, Any>
}
```

## Compilation API

### Script Compilation Interface
```kotlin
/**
 * Compilador de scripts Kotlin (.kts) para pipelines
 */
interface ScriptCompiler {
    
    /**
     * Compila script a pipeline ejecutable
     * @param script Código Kotlin del pipeline
     * @param config Configuración de compilación
     * @return Pipeline compilado
     */
    suspend fun compile(
        script: String, 
        config: CompilationConfig = CompilationConfig.default()
    ): CompiledPipeline
    
    /**
     * Compila desde archivo
     * @param file Archivo .kts
     * @param config Configuración de compilación
     * @return Pipeline compilado
     */
    suspend fun compileFile(
        file: Path,
        config: CompilationConfig = CompilationConfig.default()
    ): CompiledPipeline
    
    /**
     * Evalúa pipeline compilado
     * @param compiled Pipeline compilado
     * @param context Contexto de ejecución
     * @return Instancia del pipeline
     */
    suspend fun evaluate(
        compiled: CompiledPipeline,
        context: ExecutionContext
    ): Pipeline
    
    /**
     * Invalida cache de compilación
     */
    fun invalidateCache()
    
    /**
     * Estadísticas del compilador
     */
    fun getStats(): CompilationStats
}

/**
 * Pipeline compilado listo para ejecución
 */
interface CompiledPipeline {
    
    /**
     * ID único de la compilación
     */
    val compilationId: String
    
    /**
     * Hash del script original
     */
    val scriptHash: String
    
    /**
     * Timestamp de compilación
     */
    val compiledAt: Instant
    
    /**
     * Dependencias del script
     */
    val dependencies: List<Dependency>
    
    /**
     * Metadatos de compilación
     */
    val metadata: CompilationMetadata
    
    /**
     * Verifica si el pipeline compilado es válido
     */
    fun isValid(): Boolean
}
```

## Execution Engine API

### Pipeline Executor Interface
```kotlin
/**
 * Motor de ejecución de pipelines con soporte para paralelismo
 */
interface PipelineExecutor {
    
    /**
     * Ejecuta pipeline completo
     * @param pipeline Pipeline a ejecutar
     * @return Resultado de la ejecución
     */
    suspend fun execute(pipeline: Pipeline): PipelineResult
    
    /**
     * Ejecuta stage individual
     * @param stage Stage a ejecutar
     * @param context Contexto de ejecución
     * @return Resultado del stage
     */
    suspend fun executeStage(stage: Stage, context: ExecutionContext): StageResult
    
    /**
     * Ejecuta step individual
     * @param step Step a ejecutar  
     * @param context Contexto de ejecución
     * @return Resultado del step
     */
    suspend fun executeStep(step: PipelineStep, context: ExecutionContext): StepResult
    
    /**
     * Cancela ejecución en progreso
     * @param executionId ID de la ejecución a cancelar
     */
    suspend fun cancel(executionId: String)
    
    /**
     * Pausa ejecución (para input steps)
     * @param executionId ID de la ejecución
     * @param resumeToken Token para reanudar
     */
    suspend fun pause(executionId: String, resumeToken: String)
    
    /**
     * Reanuda ejecución pausada
     * @param resumeToken Token de reanudación
     * @param input Input del usuario
     */
    suspend fun resume(resumeToken: String, input: Any?)
    
    /**
     * Estado actual de ejecuciones
     */
    fun getExecutionStatus(): Map<String, ExecutionStatus>
}
```

## Result Types

### Pipeline Results
```kotlin
/**
 * Resultado completo de ejecución del pipeline
 */
data class PipelineResult(
    val status: PipelineStatus,
    val stages: List<StageResult>,
    val duration: Duration,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val executionId: String,
    val metadata: Map<String, Any> = emptyMap()
) {
    val isSuccess: Boolean
        get() = status == PipelineStatus.SUCCESS
        
    val isFailure: Boolean  
        get() = status == PipelineStatus.FAILURE
        
    val isCancelled: Boolean
        get() = status == PipelineStatus.CANCELLED
}

/**
 * Estado del pipeline
 */
enum class PipelineStatus {
    PENDING,
    RUNNING, 
    SUCCESS,
    FAILURE,
    CANCELLED,
    PAUSED
}

/**
 * Resultado de ejecución de un stage
 */
data class StageResult(
    val stageName: String,
    val status: StageStatus,
    val steps: List<StepResult>,
    val duration: Duration,
    val agent: Agent? = null
)

enum class StageStatus {
    SUCCESS,
    FAILURE, 
    SKIPPED,
    CANCELLED
}
```

## Configuration Types

### Compilation Configuration
```kotlin
/**
 * Configuración para compilación de scripts
 */
data class CompilationConfig(
    val jvmTarget: JvmTarget = JvmTarget.JVM_17,
    val dependencies: List<String> = emptyList(),
    val repositories: List<String> = defaultRepositories,
    val imports: List<String> = defaultImports,
    val enableCache: Boolean = true,
    val strictMode: Boolean = false
) {
    companion object {
        fun default() = CompilationConfig()
        
        private val defaultRepositories = listOf(
            "https://repo1.maven.org/maven2/",
            "https://jcenter.bintray.com/"
        )
        
        private val defaultImports = listOf(
            "hodei.dsl.*",
            "hodei.steps.*",
            "kotlinx.coroutines.*"
        )
    }
}
```

### Executor Configuration  
```kotlin
/**
 * Configuración del motor de ejecución
 */
data class ExecutorConfig(
    val maxConcurrency: Int = Runtime.getRuntime().availableProcessors(),
    val defaultTimeout: Duration = Duration.ofMinutes(30),
    val enableMetrics: Boolean = true,
    val logLevel: LogLevel = LogLevel.INFO,
    val workspaceCleanup: WorkspaceCleanupPolicy = WorkspaceCleanupPolicy.AFTER_BUILD
)

enum class WorkspaceCleanupPolicy {
    NEVER,
    AFTER_BUILD,
    BEFORE_BUILD,
    ALWAYS
}
```

## Error Handling

### Exception Hierarchy
```kotlin
/**
 * Excepción base del sistema
 */
sealed class HodeiException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Errores de compilación
 */
class CompilationException(
    val diagnostics: List<Diagnostic>
) : HodeiException("Script compilation failed")

/**
 * Errores de ejecución
 */
class ExecutionException(
    val executionId: String,
    message: String,
    cause: Throwable? = null
) : HodeiException(message, cause)

/**
 * Errores de plugin
 */
class PluginException(
    val pluginId: String,
    message: String,
    cause: Throwable? = null
) : HodeiException("Plugin '$pluginId': $message", cause)

/**
 * Errores de validación
 */
data class ValidationError(
    val field: String,
    val message: String,
    val code: String
)
```

---

## Garantías de la API

### 🔒 Backward Compatibility
- **Semantic Versioning**: Cambios breaking solo en major versions
- **Deprecation Policy**: Avisos 2 versiones antes de remover APIs
- **Migration Guides**: Documentación completa para upgrades

### 🎯 Type Safety
- **Compile-time Validation**: Errores detectados en build time
- **Null Safety**: Uso completo de tipos nullable de Kotlin  
- **Immutable by Default**: Objetos inmutables donde sea posible

### ⚡ Performance
- **Non-blocking**: Todas las operaciones I/O son suspend functions
- **Memory Efficient**: Uso optimizado de memoria con lazy evaluation
- **Cancellation Support**: Proper cancellation con Kotlin coroutines

Esta especificación define las bases sólidas para un sistema robusto, extensible y compatible con Jenkins.