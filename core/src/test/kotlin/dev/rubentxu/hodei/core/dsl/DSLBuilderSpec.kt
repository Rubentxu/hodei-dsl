package dev.rubentxu.hodei.core.dsl

import dev.rubentxu.hodei.core.domain.model.*
import dev.rubentxu.hodei.core.dsl.builders.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.assertions.throwables.shouldThrow
import kotlin.time.Duration.Companion.minutes

/**
 * BDD Specification for DSL Builders
 * 
 * Tests the type-safe DSL builders with @DslMarker scope safety,
 * ensuring Jenkins API compatibility and intuitive fluent API.
 */
class DSLBuilderSpec : BehaviorSpec({
    
    given("a pipeline DSL") {
        `when`("building a simple pipeline") {
            then("should create pipeline with basic configuration") {
                val pipeline = pipeline {
                    stage("Build") {
                        steps {
                            sh("./gradlew build")
                            echo("Build completed")
                        }
                    }
                }
                
                pipeline shouldNotBe null
                pipeline.stages shouldHaveSize 1
                pipeline.stages[0].name shouldBe "Build"
                pipeline.stages[0].steps shouldHaveSize 2
            }
        }
        
        `when`("building with global configuration") {
            then("should configure agent and environment") {
                val pipeline = pipeline {
                    agent {
                        docker {
                            image = "gradle:7.5-jdk17"
                            args = "-v /var/run/docker.sock:/var/run/docker.sock"
                        }
                    }
                    
                    environment {
                        set("JAVA_HOME", "/usr/lib/jvm/java-17")
                        set("GRADLE_OPTS", "-Xmx2g")
                    }
                    
                    stage("Test") {
                        steps {
                            sh("./gradlew test")
                        }
                    }
                }
                
                pipeline.agent shouldNotBe null
                pipeline.agent.shouldBeInstanceOf<Agent.Docker>()
                val dockerAgent = pipeline.agent as Agent.Docker
                dockerAgent.image shouldBe "gradle:7.5-jdk17"
                
                pipeline.globalEnvironment shouldBe mapOf(
                    "JAVA_HOME" to "/usr/lib/jvm/java-17",
                    "GRADLE_OPTS" to "-Xmx2g"
                )
            }
        }
        
        `when`("building with multiple stages") {
            then("should create pipeline with all stages in order") {
                val pipeline = pipeline {
                    stage("Build") {
                        steps {
                            sh("./gradlew build")
                        }
                    }
                    
                    stage("Test") {
                        steps {
                            sh("./gradlew test")
                        }
                    }
                    
                    stage("Deploy") {
                        `when` {
                            branch("main")
                        }
                        steps {
                            sh("./deploy.sh")
                        }
                    }
                }
                
                pipeline.stages shouldHaveSize 3
                pipeline.stages.map { it.name } shouldBe listOf("Build", "Test", "Deploy")
                
                // Deploy stage should have when condition
                val deployStage = pipeline.stages[2]
                deployStage.whenCondition shouldNotBe null
                deployStage.whenCondition.shouldBeInstanceOf<WhenCondition.Branch>()
            }
        }
    }
    
    given("a stage DSL") {
        `when`("configuring stage with agent") {
            then("should override global agent") {
                val pipeline = pipeline {
                    agent {
                        any()
                    }
                    
                    stage("Build") {
                        agent {
                            docker {
                                image = "maven:3.8-jdk17"
                            }
                        }
                        steps {
                            sh("mvn clean install")
                        }
                    }
                }
                
                val buildStage = pipeline.stages[0]
                buildStage.agent shouldNotBe null
                buildStage.agent.shouldBeInstanceOf<Agent.Docker>()
                
                val dockerAgent = buildStage.agent as Agent.Docker
                dockerAgent.image shouldBe "maven:3.8-jdk17"
            }
        }
        
        `when`("configuring stage with environment") {
            then("should set stage-specific environment") {
                val pipeline = pipeline {
                    environment {
                        set("GLOBAL_VAR", "global_value")
                    }
                    
                    stage("Deploy") {
                        environment {
                            set("DEPLOY_ENV", "production")
                            set("REGION", "us-west-2")
                        }
                        steps {
                            sh("echo Deploying to \$DEPLOY_ENV")
                        }
                    }
                }
                
                val deployStage = pipeline.stages[0]
                deployStage.environment shouldBe mapOf(
                    "DEPLOY_ENV" to "production",
                    "REGION" to "us-west-2"
                )
            }
        }
        
        `when`("configuring stage with when conditions") {
            then("should create complex when conditions") {
                val pipeline = pipeline {
                    stage("Deploy") {
                        `when` {
                            allOf {
                                branch("main")
                                environment("DEPLOY_ENABLED", "true")
                            }
                        }
                        steps {
                            sh("./deploy.sh")
                        }
                    }
                }
                
                val deployStage = pipeline.stages[0]
                deployStage.whenCondition shouldNotBe null
                deployStage.whenCondition.shouldBeInstanceOf<WhenCondition.And>()
                
                val andCondition = deployStage.whenCondition as WhenCondition.And
                andCondition.conditions shouldHaveSize 2
            }
        }
        
        `when`("configuring stage with post actions") {
            then("should define post execution behavior") {
                val pipeline = pipeline {
                    stage("Test") {
                        steps {
                            sh("./gradlew test")
                        }
                        post {
                            always {
                                publishTestResults("**/test-results/**/*.xml")
                                archiveArtifacts("build/reports/**/*")
                            }
                            failure {
                                emailext(
                                    to = "team@company.com",
                                    subject = "Test Failed: \${env.JOB_NAME}",
                                    body = "The test stage has failed. Please check the logs."
                                )
                            }
                        }
                    }
                }
                
                val testStage = pipeline.stages[0]
                testStage.post shouldHaveSize 2
                
                val alwaysAction = testStage.post.find { it.condition == PostCondition.ALWAYS }
                alwaysAction shouldNotBe null
                alwaysAction!!.steps shouldHaveSize 2
            }
        }
    }
    
    given("a steps DSL") {
        `when`("using basic steps") {
            then("should create different step types") {
                val pipeline = pipeline {
                    stage("Build") {
                        steps {
                            echo("Starting build process")
                            sh("./gradlew clean")
                            sh("./gradlew build")
                            
                            dir("subproject") {
                                sh("npm install")
                                sh("npm run build")
                            }
                            
                            withEnv(listOf("NODE_ENV=production", "API_URL=https://api.prod.com")) {
                                sh("npm run deploy")
                            }
                        }
                    }
                }
                
                val buildStage = pipeline.stages[0]
                buildStage.steps shouldHaveSize 5
                
                buildStage.steps[0].shouldBeInstanceOf<Step.Echo>()
                buildStage.steps[1].shouldBeInstanceOf<Step.Shell>()
                buildStage.steps[3].shouldBeInstanceOf<Step.Dir>()
                buildStage.steps[4].shouldBeInstanceOf<Step.WithEnv>()
            }
        }
        
        `when`("using control flow steps") {
            then("should create parallel and retry steps") {
                val pipeline = pipeline {
                    stage("Test") {
                        steps {
                            parallel {
                                branch("Unit Tests") {
                                    sh("./gradlew test")
                                }
                                branch("Integration Tests") {
                                    sh("./gradlew integrationTest")
                                }
                                branch("E2E Tests") {
                                    sh("./gradlew e2eTest")
                                }
                            }
                            
                            retry(3) {
                                sh("curl -f https://api.service.com/health")
                            }
                            
                            timeout(5.minutes) {
                                sh("./long-running-process.sh")
                            }
                        }
                    }
                }
                
                val testStage = pipeline.stages[0]
                testStage.steps shouldHaveSize 3
                
                // Verify parallel step
                val parallelStep = testStage.steps[0]
                parallelStep.shouldBeInstanceOf<Step.Parallel>()
                
                // Verify retry step  
                val retryStep = testStage.steps[1]
                retryStep.shouldBeInstanceOf<Step.Retry>()
                
                // Verify timeout step
                val timeoutStep = testStage.steps[2]
                timeoutStep.shouldBeInstanceOf<Step.Timeout>()
            }
        }
        
        `when`("using Jenkins-specific steps") {
            then("should create artifact and test steps") {
                val pipeline = pipeline {
                    stage("Archive") {
                        steps {
                            archiveArtifacts {
                                artifacts = "build/libs/*.jar"
                                allowEmptyArchive = true
                                fingerprint = true
                            }
                            
                            publishTestResults {
                                testResultsPattern = "**/test-results/**/*.xml"
                                allowEmptyResults = false
                            }
                            
                            stash {
                                name = "build-artifacts"
                                includes = "build/**/*"
                                excludes = "build/tmp/**"
                            }
                            
                            unstash("dependencies")
                        }
                    }
                }
                
                val archiveStage = pipeline.stages[0]
                archiveStage.steps shouldHaveSize 4
                
                archiveStage.steps[0].shouldBeInstanceOf<Step.ArchiveArtifacts>()
                archiveStage.steps[1].shouldBeInstanceOf<Step.PublishTestResults>()
                archiveStage.steps[2].shouldBeInstanceOf<Step.Stash>()
                archiveStage.steps[3].shouldBeInstanceOf<Step.Unstash>()
            }
        }
    }
    
    given("DSL type safety") {
        `when`("attempting invalid nesting") {
            then("should prevent compilation errors with @DslMarker") {
                // This test ensures that @DslMarker prevents invalid scope access
                // The actual compilation test would be handled by compiler tests
                val pipeline = pipeline {
                    stage("Build") {
                        steps {
                            sh("./gradlew build")
                            // This should not compile if @DslMarker is working:
                            // stage("Invalid") { } // Should be prevented by @DslMarker
                        }
                    }
                }
                
                pipeline.stages shouldHaveSize 1
                pipeline.stages[0].name shouldBe "Build"
            }
        }
        
        `when`("using nested DSL contexts") {
            then("should maintain proper scope isolation") {
                val pipeline = pipeline {
                    stage("Complex") {
                        steps {
                            dir("frontend") {
                                sh("npm install")
                                withEnv(listOf("NODE_ENV=production")) {
                                    sh("npm run build")
                                }
                            }
                            
                            parallel {
                                branch("Test Frontend") {
                                    dir("frontend") {
                                        sh("npm test")
                                    }
                                }
                                branch("Test Backend") {
                                    dir("backend") {
                                        sh("./gradlew test")
                                    }
                                }
                            }
                        }
                    }
                }
                
                val complexStage = pipeline.stages[0]
                complexStage.steps shouldHaveSize 2
                
                // Verify nested dir step
                val dirStep = complexStage.steps[0] as Step.Dir
                dirStep.path shouldBe "frontend"
                dirStep.steps shouldHaveSize 2
                
                // Verify nested withEnv
                val withEnvStep = dirStep.steps[1] as Step.WithEnv
                withEnvStep.environment shouldContain "NODE_ENV=production"
            }
        }
    }
    
    given("DSL validation") {
        `when`("creating invalid pipeline configuration") {
            then("should throw meaningful validation errors") {
                shouldThrow<IllegalArgumentException> {
                    pipeline {
                        // Empty pipeline should fail
                    }
                }.message shouldBe "Pipeline must contain at least one stage"
                
                shouldThrow<IllegalArgumentException> {
                    pipeline {
                        stage("") {
                            steps {
                                sh("echo test")
                            }
                        }
                    }
                }.message shouldBe "Stage name cannot be blank"
                
                shouldThrow<IllegalArgumentException> {
                    pipeline {
                        stage("Test") {
                            steps {
                                // Empty steps should fail
                            }
                        }
                    }
                }.message shouldBe "Stage must contain at least one step"
            }
        }
        
        `when`("creating duplicate stage names") {
            then("should prevent duplicate stages") {
                shouldThrow<IllegalArgumentException> {
                    pipeline {
                        stage("Build") {
                            steps { sh("./gradlew build") }
                        }
                        stage("Build") { // Duplicate name
                            steps { sh("./gradlew test") }
                        }
                    }
                }.message shouldBe "Stage with name 'Build' already exists"
            }
        }
    }
    
    given("DSL convenience methods") {
        `when`("using shorthand syntax") {
            then("should create equivalent structures") {
                val pipeline1 = pipeline {
                    stage("Build") {
                        steps {
                            sh("./gradlew build")
                        }
                    }
                }
                
                val pipeline2 = pipeline {
                    stage("Build") {
                        sh("./gradlew build") // Shorthand for single step
                    }
                }
                
                pipeline1.stages[0].steps shouldBe pipeline2.stages[0].steps
            }
        }
        
        `when`("using fluent configuration") {
            then("should chain configuration methods") {
                val pipeline = pipeline {
                    stage("Deploy") {
                        agent { docker { image = "kubectl:latest" } }
                        environment { set("KUBECONFIG", "/etc/kubeconfig") }
                        `when` { branch("main") }
                        
                        steps {
                            sh("kubectl apply -f deployment.yaml")
                        }
                        
                        post {
                            success {
                                echo("Deployment successful!")
                            }
                        }
                    }
                }
                
                val deployStage = pipeline.stages[0]
                deployStage.agent shouldNotBe null
                deployStage.environment shouldBe mapOf("KUBECONFIG" to "/etc/kubeconfig")
                deployStage.whenCondition shouldNotBe null
                deployStage.post shouldHaveSize 1
            }
        }
    }
})