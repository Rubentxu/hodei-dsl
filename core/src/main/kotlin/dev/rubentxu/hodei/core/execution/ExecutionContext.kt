package dev.rubentxu.hodei.core.execution

import java.nio.file.Path
import java.time.Instant
import java.util.*

/**
 * Execution context interface
 * 
 * Contexto de ejecución que se pasa a todos los steps del pipeline.
 * Contiene información del entorno, directorios, variables, logger, metrics, etc.
 * 
 * Designed to be immutable and thread-safe for concurrent pipeline execution.
 */
public interface ExecutionContext {
    
    /**
     * Directorio de trabajo actual
     */
    public val workDir: Path
    
    /**
     * Variables de entorno disponibles (immutable view)
     */
    public val environment: Map<String, String>
    
    /**
     * Logger para output del pipeline
     */
    public val logger: PipelineLogger
    
    /**
     * ID único de esta ejecución
     */
    public val executionId: String
    
    /**
     * ID del build/job
     */
    public val buildId: String
    
    /**
     * Información del workspace
     */
    public val workspace: WorkspaceInfo
    
    /**
     * Información del job/pipeline
     */
    public val jobInfo: JobInfo
    
    /**
     * Directorio para artefactos
     */
    public val artifactDir: Path
    
    /**
     * Launcher para ejecutar comandos
     */
    public val launcher: CommandLauncher
    
    /**
     * Métricas del pipeline
     */
    public val metrics: PipelineMetrics
    
    /**
     * Step executor for nested step execution
     */
    public val stepExecutor: StepExecutor
    
    /**
     * Crea un contexto derivado con modificaciones
     * Following immutable pattern for thread safety
     */
    public fun copy(
        workDir: Path = this.workDir,
        environment: Map<String, String> = this.environment,
        launcher: CommandLauncher = this.launcher
    ): ExecutionContext
    
    /**
     * Validates the execution context state
     * @return List of validation errors, empty if valid
     */
    public fun validate(): List<ValidationError>
    
    public companion object {
        
        private val defaultFactory: ExecutionContextFactory = DefaultExecutionContextFactory()
        
        /**
         * Crea contexto por defecto para testing/desarrollo
         */
        public fun default(): ExecutionContext = defaultFactory.createDefault()
        
        /**
         * Creates a new builder for execution context
         */
        public fun builder(): ExecutionContextBuilder = ExecutionContextBuilder()
        
        /**
         * Creates environment-specific execution context
         * @param environment Target environment (development, staging, production)
         */
        public fun forEnvironment(environment: String): ExecutionContext = 
            defaultFactory.createForEnvironment(environment)
        
        /**
         * Creates execution context with custom factory
         * @param factory Custom factory implementation
         */
        public fun withFactory(factory: ExecutionContextFactory): ExecutionContextFactory = factory
        
        /**
         * Creates execution context from configuration
         * @param config Execution context configuration
         */
        public fun fromConfig(config: ExecutionContextConfig): ExecutionContext = 
            defaultFactory.create(config)
    }
}