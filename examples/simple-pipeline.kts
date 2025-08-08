#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("dev.rubentxu.hodei:core:1.0.0-SNAPSHOT")

import dev.rubentxu.hodei.core.dsl.pipeline

/**
 * Simple pipeline example demonstrating the Hodei DSL
 * 
 * This example showcases:
 * - Type-safe DSL construction
 * - Jenkins-compatible syntax
 * - Modern Kotlin features (coroutines, sealed classes, etc.)
 * - @DslMarker scope safety
 * - Comprehensive step types
 */

val myPipeline = pipeline {
    // Global agent configuration
    agent {
        docker {
            image = "gradle:7.5-jdk17"
            args = "-v /var/run/docker.sock:/var/run/docker.sock"
        }
    }
    
    // Global environment variables
    environment {
        set("JAVA_HOME", "/usr/lib/jvm/java-17")
        set("GRADLE_OPTS", "-Xmx2g")
    }
    
    // Build stage
    stage("Build") {
        steps {
            echo("Starting build process...")
            sh("./gradlew clean build")
            
            archiveArtifacts {
                artifacts = "build/libs/*.jar"
                fingerprint = true
                allowEmptyArchive = false
            }
        }
    }
    
    // Test stage with parallel execution
    stage("Test") {
        steps {
            parallel {
                branch("Unit Tests") {
                    sh("./gradlew test")
                    publishTestResults("**/test-results/**/*.xml")
                }
                
                branch("Integration Tests") {
                    sh("./gradlew integrationTest")
                }
                
                branch("Code Quality") {
                    sh("./gradlew sonarqube")
                }
            }
        }
        
        post {
            always {
                publishTestResults {
                    testResultsPattern = "**/build/test-results/**/*.xml"
                    allowEmptyResults = true
                }
            }
        }
    }
    
    // Conditional deployment stage
    stage("Deploy") {
        `when` {
            branch("main")
        }
        
        environment {
            set("DEPLOY_ENV", "production")
        }
        
        steps {
            echo("Deploying to production...")
            sh("./deploy-production.sh")
            
            stash {
                name = "deployment-artifacts"
                includes = "deployment/**"
            }
        }
        
        post {
            success {
                echo("Deployment successful! 🎉")
            }
            
            failure {
                emailext(
                    to = "devops@company.com",
                    subject = "Production Deployment Failed",
                    body = "The production deployment has failed. Please check the logs."
                )
            }
        }
    }
}

println("Pipeline created successfully!")
println("Pipeline ID: ${myPipeline.id}")
println("Number of stages: ${myPipeline.stages.size}")
println("Global environment: ${myPipeline.globalEnvironment}")