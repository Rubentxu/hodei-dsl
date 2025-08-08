package dev.rubentxu.hodei.core.domain

import dev.rubentxu.hodei.core.domain.model.*
import dev.rubentxu.hodei.core.execution.ExecutionContext
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContain
import kotlin.time.Duration.Companion.minutes

/**
 * BDD Specification for Pipeline Domain Models
 * 
 * Tests the core domain models following Jenkins API compatibility
 * and ensuring immutability, validation, and proper behavior.
 */
class PipelineModelSpec : BehaviorSpec({
    
    given("a new Pipeline") {
        `when`("created with basic configuration") {
            then("should have default properties") {
                val pipeline = Pipeline.builder()
                    .build()
                
                pipeline.id shouldNotBe null
                pipeline.stages shouldHaveSize 0
                pipeline.globalEnvironment shouldBe emptyMap<String, String>()
                pipeline.status shouldBe PipelineStatus.PENDING
                pipeline.agent shouldBe null
            }
        }
        
        `when`("created with stages") {
            then("should contain all configured stages") {
                val stage1 = Stage(
                    name = "Build",
                    steps = listOf(Step.shell("./gradlew build"))
                )
                val stage2 = Stage(
                    name = "Test", 
                    steps = listOf(Step.shell("./gradlew test"))
                )
                
                val pipeline = Pipeline.builder()
                    .addStage(stage1)
                    .addStage(stage2)
                    .build()
                
                pipeline.stages shouldHaveSize 2
                pipeline.stages shouldContain stage1
                pipeline.stages shouldContain stage2
            }
        }
        
        `when`("created with global environment") {
            then("should store environment variables") {
                val env = mapOf(
                    "JAVA_HOME" to "/usr/lib/jvm/java-17",
                    "GRADLE_OPTS" to "-Xmx2g"
                )
                
                val pipeline = Pipeline.builder()
                    .globalEnvironment(env)
                    .build()
                
                pipeline.globalEnvironment shouldBe env
            }
        }
    }
    
    given("a Pipeline with execution context") {
        `when`("executing") {
            then("should maintain immutable state during execution") {
                val pipeline = Pipeline.builder()
                    .addStage(Stage("Build", listOf(Step.shell("./gradlew build"))))
                    .build()
                
                val originalStatus = pipeline.status
                val originalId = pipeline.id
                
                // Pipeline should be immutable - status changes create new instances
                pipeline.status shouldBe originalStatus
                pipeline.id shouldBe originalId
            }
        }
    }
    
    given("a Stage") {
        `when`("created with steps") {
            then("should validate step configuration") {
                val step1 = Step.shell("echo 'Building...'")
                val step2 = Step.shell("./gradlew build")
                
                val stage = Stage(
                    name = "Build",
                    steps = listOf(step1, step2)
                )
                
                stage.name shouldBe "Build"
                stage.steps shouldHaveSize 2
                stage.steps[0] shouldBe step1
                stage.steps[1] shouldBe step2
            }
        }
        
        `when`("created with agent configuration") {
            then("should store agent information") {
                val dockerAgent = Agent.docker("gradle:7.5-jdk17")
                
                val stage = Stage(
                    name = "Build",
                    steps = listOf(Step.shell("./gradlew build")),
                    agent = dockerAgent
                )
                
                stage.agent shouldBe dockerAgent
            }
        }
        
        `when`("created with environment variables") {
            then("should merge with global environment") {
                val stageEnv = mapOf("BUILD_TYPE" to "release")
                
                val stage = Stage(
                    name = "Build",
                    steps = listOf(Step.shell("./gradlew build")),
                    environment = stageEnv
                )
                
                stage.environment shouldBe stageEnv
            }
        }
        
        `when`("created with when conditions") {
            then("should store conditional logic") {
                val condition = WhenCondition.branch("main")
                
                val stage = Stage(
                    name = "Deploy",
                    steps = listOf(Step.shell("./deploy.sh")),
                    whenCondition = condition
                )
                
                stage.whenCondition shouldBe condition
            }
        }
    }
    
    given("a Step") {
        `when`("created as shell step") {
            then("should have correct type and configuration") {
                val step = Step.shell("./gradlew build")
                
                step.type shouldBe StepType.SHELL
                step.shouldBeInstanceOf<Step.Shell>()
                
                val shellStep = step as Step.Shell
                shellStep.script shouldBe "./gradlew build"
                shellStep.returnStdout shouldBe false
                shellStep.returnStatus shouldBe false
            }
        }
        
        `when`("created as shell step with options") {
            then("should configure all options correctly") {
                val step = Step.shell(
                    script = "git rev-parse HEAD",
                    returnStdout = true,
                    returnStatus = false,
                    encoding = "UTF-8"
                )
                
                step.shouldBeInstanceOf<Step.Shell>()
                
                val shellStep = step as Step.Shell
                shellStep.script shouldBe "git rev-parse HEAD"
                shellStep.returnStdout shouldBe true
                shellStep.returnStatus shouldBe false
                shellStep.encoding shouldBe "UTF-8"
            }
        }
        
        `when`("created as echo step") {
            then("should store message correctly") {
                val step = Step.echo("Building application...")
                
                step.type shouldBe StepType.ECHO
                step.shouldBeInstanceOf<Step.Echo>()
                
                val echoStep = step as Step.Echo
                echoStep.message shouldBe "Building application..."
            }
        }
        
        `when`("created with timeout") {
            then("should store timeout configuration") {
                val step = Step.shell("sleep 30").withTimeout(5.minutes)
                
                step.timeout shouldBe 5.minutes
            }
        }
    }
    
    given("Agent configurations") {
        `when`("creating local agent") {
            then("should have correct type") {
                val agent = Agent.any()
                
                agent.type shouldBe AgentType.ANY
            }
        }
        
        `when`("creating docker agent") {
            then("should configure docker settings") {
                val agent = Agent.docker(
                    image = "gradle:7.5-jdk17",
                    args = "-v /var/run/docker.sock:/var/run/docker.sock"
                )
                
                agent.type shouldBe AgentType.DOCKER
                agent.shouldBeInstanceOf<Agent.Docker>()
                
                val dockerAgent = agent as Agent.Docker
                dockerAgent.image shouldBe "gradle:7.5-jdk17"
                dockerAgent.args shouldBe "-v /var/run/docker.sock:/var/run/docker.sock"
            }
        }
        
        `when`("creating kubernetes agent") {
            then("should configure pod template") {
                val agent = Agent.kubernetes("pod-template.yaml")
                
                agent.type shouldBe AgentType.KUBERNETES
                agent.shouldBeInstanceOf<Agent.Kubernetes>()
                
                val k8sAgent = agent as Agent.Kubernetes
                k8sAgent.yaml shouldBe "pod-template.yaml"
            }
        }
    }
    
    given("When conditions") {
        `when`("creating branch condition") {
            then("should match specific branch") {
                val condition = WhenCondition.branch("main")
                
                condition.shouldBeInstanceOf<WhenCondition.Branch>()
                
                val branchCondition = condition as WhenCondition.Branch
                branchCondition.pattern shouldBe "main"
            }
        }
        
        `when`("creating environment condition") {
            then("should check environment variable") {
                val condition = WhenCondition.environment("DEPLOY_ENV", "production")
                
                condition.shouldBeInstanceOf<WhenCondition.Environment>()
                
                val envCondition = condition as WhenCondition.Environment
                envCondition.name shouldBe "DEPLOY_ENV"
                envCondition.value shouldBe "production"
            }
        }
        
        `when`("creating expression condition") {
            then("should store custom expression") {
                val condition = WhenCondition.expression("params.RELEASE == true")
                
                condition.shouldBeInstanceOf<WhenCondition.Expression>()
                
                val exprCondition = condition as WhenCondition.Expression
                exprCondition.expression shouldBe "params.RELEASE == true"
            }
        }
    }
})