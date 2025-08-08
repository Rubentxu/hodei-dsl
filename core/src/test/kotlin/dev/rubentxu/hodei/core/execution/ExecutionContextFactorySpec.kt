package dev.rubentxu.hodei.core.execution

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path

/**
 * BDD Specification for ExecutionContextFactory
 * 
 * Tests the factory pattern implementation including dependency injection
 * ports, configuration-based creation, and environment-specific factories.
 * Ensures loose coupling and improved testability through factory abstraction.
 */
class ExecutionContextFactorySpec : BehaviorSpec({
    
    given("an ExecutionContextFactory") {
        val factory = DefaultExecutionContextFactory()
        
        `when`("creating default execution context") {
            then("should create context with all components") {
                val context = factory.createDefault()
                
                context.workDir shouldNotBe null
                context.environment shouldNotBe null
                context.logger.shouldBeInstanceOf<PipelineLogger>()
                context.launcher.shouldBeInstanceOf<CommandLauncher>()
                context.workspace.shouldBeInstanceOf<WorkspaceInfo>()
                context.jobInfo.shouldBeInstanceOf<JobInfo>()
                context.metrics.shouldBeInstanceOf<PipelineMetrics>()
            }
        }
        
        `when`("creating environment-specific context") {
            then("should configure for development environment") {
                val context = factory.createForEnvironment("development")
                
                context.environment["ENVIRONMENT"] shouldBe "development"
                context.executionId.startsWith("dev-") shouldBe true
            }
            
            then("should configure for production environment") {
                val context = factory.createForEnvironment("production")
                
                context.environment["ENVIRONMENT"] shouldBe "production"
                context.executionId.startsWith("pro-") shouldBe true
            }
        }
        
        `when`("creating context from configuration") {
            then("should use provided configuration") {
                val customWorkDir = Path.of("/custom/workspace")
                val customEnv = mapOf("CUSTOM_VAR" to "custom_value")
                
                val config = ExecutionContextConfig(
                    workDir = customWorkDir,
                    environment = customEnv,
                    executionId = "custom-execution"
                )
                
                val context = factory.create(config)
                
                context.workDir shouldBe customWorkDir
                context.environment["CUSTOM_VAR"] shouldBe "custom_value"
                context.executionId shouldBe "custom-execution"
            }
        }
    }
    
    given("ExecutionContext companion object with factory") {
        `when`("using fromConfig method") {
            then("should create context from configuration") {
                val config = ExecutionContextConfig(
                    environment = mapOf("TEST_VAR" to "test_value"),
                    executionId = "test-execution"
                )
                
                val context = ExecutionContext.fromConfig(config)
                
                context.environment["TEST_VAR"] shouldBe "test_value"
                context.executionId shouldBe "test-execution"
            }
        }
        
        `when`("using withFactory method") {
            then("should return custom factory") {
                val customFactory = DefaultExecutionContextFactory()
                val returnedFactory = ExecutionContext.withFactory(customFactory)
                
                returnedFactory shouldBe customFactory
            }
        }
    }
    
    given("Component factories") {
        `when`("using different component factories") {
            then("should create components with different configurations") {
                val loggerFactory = DefaultPipelineLoggerFactory()
                val launcherFactory = DefaultCommandLauncherFactory()
                val workspaceFactory = DefaultWorkspaceInfoFactory()
                val jobInfoFactory = DefaultJobInfoFactory()
                
                val logger = loggerFactory.createDefault()
                val localLauncher = launcherFactory.createLocal()
                val dockerLauncher = launcherFactory.createDocker("ubuntu:20.04")
                val workspace = workspaceFactory.createDefault()
                val jobInfo = jobInfoFactory.createDefault()
                
                logger.shouldBeInstanceOf<DefaultPipelineLogger>()
                localLauncher.shouldBeInstanceOf<LocalCommandLauncher>()
                dockerLauncher.shouldBeInstanceOf<DockerCommandLauncher>()
                workspace.shouldBeInstanceOf<WorkspaceInfo>()
                jobInfo.shouldBeInstanceOf<JobInfo>()
            }
        }
    }
})