// Test integration between Pipeline DSL and Compiler
val myPipeline = pipeline {
    stage("Build") {
        steps {
            sh("echo 'Building project...'")
            echo("Build stage completed")
        }
    }
    
    stage("Test") {
        steps {
            sh("echo 'Running tests...'")
            echo("Test stage completed")
        }
    }
}

// Return the pipeline for execution
myPipeline