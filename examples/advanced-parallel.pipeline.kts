// Advanced Pipeline DSL with Parallel Execution, Retry, and Timeout
val advancedPipeline = pipeline {
    stage("Build") {
        steps {
            sh("echo 'Starting build...'")
            retry(3) {
                sh("gradle build") // May fail, retry up to 3 times
            }
            echo("Build completed successfully")
        }
    }
    
    stage("Parallel Testing") {
        steps {
            parallel {
                branch("Unit Tests") {
                    timeout(5.minutes) {
                        sh("sleep 1")
                        sh("echo 'Unit tests completed'")
                        echo("✅ Unit tests: PASSED")
                    }
                }
                
                branch("Integration Tests") {
                    retry(2) {
                        timeout(3.minutes) {
                            sh("sleep 1")
                            sh("echo 'Integration tests completed'")
                            echo("✅ Integration tests: PASSED")
                        }
                    }
                }
                
                branch("E2E Tests") {
                    timeout(10.minutes) {
                        sh("sleep 1")
                        sh("echo 'E2E tests completed'")
                        echo("✅ E2E tests: PASSED")
                    }
                }
            }
        }
    }
    
    stage("Deploy") {
        steps {
            retry(3) {
                timeout(5.minutes) {
                    sh("echo 'Deploying application...'")
                    sh("echo 'Deployment successful!'")
                }
            }
            echo("🚀 Application deployed successfully!")
        }
    }
}

// Return the pipeline for execution
advancedPipeline