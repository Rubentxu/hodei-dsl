package dev.rubentxu.hodei.core.dsl.extensions

import dev.rubentxu.hodei.core.dsl.builders.*
import dev.rubentxu.hodei.core.domain.model.Step
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Extension functions for common Pipeline DSL patterns
 * 
 * These extensions provide convenient shortcuts for frequently used
 * patterns in CI/CD pipelines, making the DSL more expressive and concise.
 */

/**
 * Common pipeline patterns
 */

/**
 * Create a build pipeline with standard stages
 */
public fun PipelineBuilder.standardBuildPipeline(
    buildCommand: String = "./gradlew build",
    testCommand: String = "./gradlew test",
    includeCodeQuality: Boolean = true
) {
    stage("Checkout") {
        steps {
            echo("Checking out source code...")
            sh("git status")
        }
    }
    
    if (includeCodeQuality) {
        stage("Code Quality") {
            steps {
                echo("Running code quality checks...")
                sh("./gradlew ktlintCheck")
                sh("./gradlew detekt")
            }
        }
    }
    
    stage("Build") {
        steps {
            echo("Building application...")
            sh(buildCommand)
        }
        post {
            always {
                echo("Build stage completed")
            }
        }
    }
    
    stage("Test") {
        steps {
            echo("Running tests...")
            sh(testCommand)
        }
        post {
            always {
                publishTestResults("**/build/test-results/**/*.xml")
            }
            success {
                echo("All tests passed!")
            }
            failure {
                echo("Tests failed - check logs")
            }
        }
    }
}

/**
 * Create a deployment pipeline
 */
public fun PipelineBuilder.deploymentPipeline(
    environment: String,
    deployCommand: String,
    healthCheckUrl: String? = null
) {
    stage("Pre-deployment") {
        steps {
            echo("Preparing for deployment to $environment...")
            sh("echo 'Deployment target: $environment'")
        }
    }
    
    stage("Deploy") {
        steps {
            echo("Deploying to $environment...")
            sh(deployCommand)
        }
        post {
            success {
                echo("Deployment to $environment successful")
            }
            failure {
                echo("Deployment to $environment failed")
            }
        }
    }
    
    if (healthCheckUrl != null) {
        stage("Health Check") {
            steps {
                echo("Performing health check...")
                retry(3) {
                    sh("curl -f $healthCheckUrl || exit 1")
                    echo("Health check passed")
                }
            }
        }
    }
}

/**
 * Common stage patterns
 */

/**
 * Create a conditional stage that runs only in specific environments
 */
public fun PipelineBuilder.conditionalStage(
    name: String,
    environments: List<String>,
    block: StageBuilder.() -> Unit
) {
    stage(name) {
        `when` {
            anyOf {
                environments.forEach { env ->
                    environment("DEPLOY_ENV", env)
                }
            }
        }
        block()
    }
}

/**
 * Create a parallel test stage with multiple test suites
 */
public fun PipelineBuilder.parallelTestStage(
    testSuites: Map<String, String>
) {
    stage("Parallel Tests") {
        steps {
            parallel {
                testSuites.forEach { (name, command) ->
                    branch(name) {
                        echo("Running $name tests...")
                        sh(command)
                        echo("$name tests completed")
                    }
                }
            }
        }
        post {
            always {
                publishTestResults("**/build/test-results/**/*.xml")
            }
        }
    }
}

/**
 * Common step patterns
 */

/**
 * Execute a command with retry and timeout
 */
public fun StepsBuilder.robustExecute(
    command: String,
    retries: Int = 3,
    timeout: Duration = 5.minutes,
    description: String = "Executing command"
) {
    echo(description)
    timeout(timeout) {
        retry(retries) {
            sh(command)
        }
    }
}

/**
 * Gradle build with common options
 */
public fun StepsBuilder.gradleBuild(
    task: String = "build",
    daemon: Boolean = false,
    parallel: Boolean = true,
    maxWorkers: Int? = null
) {
    val daemonFlag = if (daemon) "" else "--no-daemon"
    val parallelFlag = if (parallel) "--parallel" else ""
    val workersFlag = maxWorkers?.let { "--max-workers=$it" } ?: ""
    
    val command = "gradle $task $daemonFlag $parallelFlag $workersFlag".trim()
    sh(command)
}

/**
 * Docker operations
 */
public fun StepsBuilder.dockerBuild(
    imageName: String,
    dockerfilePath: String = ".",
    buildArgs: Map<String, String> = emptyMap(),
    platforms: List<String> = emptyList()
) {
    val buildArgsString = buildArgs.entries.joinToString(" ") { (key, value) ->
        "--build-arg $key=$value"
    }
    
    val platformsString = if (platforms.isNotEmpty()) {
        "--platform ${platforms.joinToString(",")}"
    } else ""
    
    val command = "docker build $buildArgsString $platformsString -t $imageName $dockerfilePath".trim()
    sh(command)
}

public fun StepsBuilder.dockerPush(imageName: String, registry: String? = null) {
    val fullImageName = if (registry != null) "$registry/$imageName" else imageName
    sh("docker push $fullImageName")
}

/**
 * Git operations
 */
public fun StepsBuilder.gitTag(tagName: String, message: String? = null) {
    val messageFlag = message?.let { "-m \"$it\"" } ?: ""
    sh("git tag $messageFlag $tagName")
    sh("git push origin $tagName")
}

public fun StepsBuilder.gitCleanup() {
    sh("git clean -fd")
    sh("git reset --hard HEAD")
}

/**
 * Notification patterns
 */
public fun StepsBuilder.slackNotification(
    webhook: String,
    message: String,
    channel: String? = null
) {
    val channelFlag = channel?.let { "-d \"channel=$it\"" } ?: ""
    sh("""
        curl -X POST -H 'Content-type: application/json' \
        --data '{"text":"$message"}' \
        $channelFlag \
        $webhook
    """.trimIndent())
}

/**
 * Artifact management patterns
 */
public fun StepsBuilder.archiveJars(pattern: String = "**/*.jar") {
    archiveArtifacts {
        artifacts = pattern
        allowEmptyArchive = true
        fingerprint = true
    }
}

public fun StepsBuilder.publishReports(
    testResults: String = "**/build/test-results/**/*.xml",
    coverageReports: String = "**/build/reports/jacoco/**/*.xml"
) {
    publishTestResults {
        testResultsPattern = testResults
        allowEmptyResults = true
    }
    
    archiveArtifacts {
        artifacts = coverageReports
        allowEmptyArchive = true
    }
}

/**
 * Environment setup patterns
 */
public fun StepsBuilder.setupJava(version: String = "17") {
    echo("Setting up Java $version...")
    sh("java -version")
    withEnv(listOf("JAVA_VERSION=$version")) {
        sh("echo 'Java version set to: \$JAVA_VERSION'")
    }
}

public fun StepsBuilder.setupNode(version: String = "18") {
    echo("Setting up Node.js $version...")
    sh("node --version")
    sh("npm --version")
}

/**
 * Database operations
 */
public fun StepsBuilder.databaseMigration(
    migrationCommand: String = "./gradlew flywayMigrate",
    environment: String = "development"
) {
    echo("Running database migration for $environment...")
    withEnv(listOf("DB_ENV=$environment")) {
        sh(migrationCommand)
    }
}

/**
 * Security scanning patterns
 */
public fun StepsBuilder.securityScan(
    tool: String = "dependency-check",
    reportFormat: String = "HTML"
) {
    echo("Running security scan with $tool...")
    when (tool.lowercase()) {
        "dependency-check" -> sh("./gradlew dependencyCheckAnalyze")
        "snyk" -> sh("snyk test")
        "trivy" -> sh("trivy fs .")
        else -> sh(tool)
    }
}

/**
 * Performance testing patterns
 */
public fun StepsBuilder.performanceTest(
    testScript: String,
    duration: Duration = 10.minutes,
    users: Int = 10
) {
    echo("Running performance test with $users users for $duration...")
    timeout(duration + 2.minutes) {
        sh("$testScript --users=$users --duration=${duration.inWholeSeconds}s")
    }
}

/**
 * Utility extensions
 */

/**
 * Conditional execution based on environment variable
 */
public fun StepsBuilder.whenEnv(envVar: String, value: String, block: StepsBuilder.() -> Unit) {
    sh("if [ \"\$$envVar\" = \"$value\" ]; then echo 'Condition met'; else exit 0; fi")
    block()
}

/**
 * Execute only on specific days (for scheduled builds)
 */
public fun StepsBuilder.onlyOnDays(vararg days: String, block: StepsBuilder.() -> Unit) {
    val dayCondition = days.joinToString("|")
    sh("if date +%A | grep -E \"$dayCondition\"; then echo 'Day condition met'; else exit 0; fi")
    block()
}

/**
 * Time-based execution
 */
public fun StepsBuilder.duringBusinessHours(block: StepsBuilder.() -> Unit) {
    sh("hour=\$(date +%H); if [ \$hour -ge 9 -a \$hour -le 17 ]; then echo 'Business hours'; else exit 0; fi")
    block()
}

/**
 * Resource cleanup patterns
 */
public fun StepsBuilder.cleanup(vararg paths: String) {
    echo("Cleaning up resources...")
    paths.forEach { path ->
        sh("rm -rf $path || true")
    }
    echo("Cleanup completed")
}