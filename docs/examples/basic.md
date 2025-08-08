# Ejemplos Básicos - Hodei Pipeline DSL

## Pipelines de Introducción

### 1. **Hello World Pipeline**

El pipeline más simple posible para empezar:

```kotlin
// hello-world.pipeline.kts
pipeline {
    agent { any() }
    
    stages {
        stage("Hello") {
            steps {
                echo("Hello, World!")
                echo("Current time: ${System.currentTimeMillis()}")
            }
        }
    }
}
```

### 2. **Pipeline con Variables de Entorno**

```kotlin
// environment-vars.pipeline.kts
pipeline {
    agent { any() }
    
    environment {
        set("APP_NAME", "my-awesome-app")
        set("VERSION", "1.0.0")
        set("BUILD_NUMBER", env("BUILD_ID") ?: "local")
    }
    
    stages {
        stage("Show Environment") {
            steps {
                echo("Building ${env.APP_NAME} version ${env.VERSION}")
                echo("Build number: ${env.BUILD_NUMBER}")
                
                sh("printenv | grep APP")
            }
        }
    }
}
```

### 3. **Pipeline con Parámetros**

```kotlin
// parameters.pipeline.kts
pipeline {
    agent { any() }
    
    parameters {
        string {
            name = "ENVIRONMENT"
            defaultValue = "development"
            description = "Target environment for deployment"
        }
        
        choice {
            name = "LOG_LEVEL"
            choices = listOf("DEBUG", "INFO", "WARN", "ERROR")
            defaultValue = "INFO"
            description = "Logging level"
        }
        
        booleanParam {
            name = "SKIP_TESTS"
            defaultValue = false
            description = "Skip test execution"
        }
    }
    
    stages {
        stage("Configuration") {
            steps {
                echo("Target environment: ${params.ENVIRONMENT}")
                echo("Log level: ${params.LOG_LEVEL}")
                echo("Skip tests: ${params.SKIP_TESTS}")
                
                script {
                    if (params.getBoolean("SKIP_TESTS")) {
                        echo("⚠️ Tests will be skipped!")
                    } else {
                        echo("✅ Tests will be executed")
                    }
                }
            }
        }
    }
}
```

## Pipelines de Construcción

### 4. **Java/Gradle Build Pipeline**

```kotlin
// java-gradle-build.pipeline.kts
pipeline {
    agent {
        docker {
            image = "gradle:7.5-jdk17"
            args = "-v gradle-cache:/home/gradle/.gradle"
        }
    }
    
    environment {
        set("GRADLE_OPTS", "-Xmx2g -Dorg.gradle.daemon=false")
        set("JAVA_TOOL_OPTIONS", "-Xmx1g")
    }
    
    stages {
        stage("Checkout") {
            steps {
                checkout(scm)
                sh("ls -la")
            }
        }
        
        stage("Build") {
            steps {
                sh("./gradlew clean build --no-daemon --parallel")
                
                // Archive build artifacts
                archiveArtifacts {
                    artifacts = "**/build/libs/*.jar"
                    fingerprint = true
                    allowEmptyArchive = false
                }
            }
        }
        
        stage("Test") {
            steps {
                sh("./gradlew test --no-daemon")
                
                // Publish test results
                junit {
                    testResults = "**/build/test-results/test/*.xml"
                    allowEmptyResults = true
                    keepLongStdio = true
                }
                
                // Publish test coverage
                publishHTML {
                    allowMissing = false
                    alwaysLinkToLastBuild = true
                    keepAll = true
                    reportDir = "build/reports/jacoco/test/html"
                    reportFiles = "index.html"
                    reportName = "Code Coverage Report"
                }
            }
        }
    }
    
    post {
        always {
            cleanWs()
        }
        
        success {
            echo("🎉 Build completed successfully!")
        }
        
        failure {
            echo("💥 Build failed!")
        }
    }
}
```

### 5. **Node.js Build Pipeline**

```kotlin
// nodejs-build.pipeline.kts
pipeline {
    agent {
        docker {
            image = "node:18-alpine"
            args = "-v npm-cache:/root/.npm"
        }
    }
    
    environment {
        set("NODE_ENV", "production")
        set("NPM_CONFIG_CACHE", "/root/.npm")
    }
    
    stages {
        stage("Setup") {
            steps {
                checkout(scm)
                sh("node --version")
                sh("npm --version")
            }
        }
        
        stage("Install Dependencies") {
            steps {
                sh("npm ci --prefer-offline --no-audit")
                
                // Cache node_modules para futuros builds
                stash {
                    name = "node_modules"
                    includes = "node_modules/**"
                    allowEmpty = false
                }
            }
        }
        
        stage("Lint & Format") {
            steps {
                sh("npm run lint")
                sh("npm run format:check")
            }
        }
        
        stage("Test") {
            parallel {
                stage("Unit Tests") {
                    steps {
                        sh("npm run test:unit -- --coverage --ci")
                        
                        junit {
                            testResults = "coverage/junit.xml"
                            allowEmptyResults = true
                        }
                    }
                }
                
                stage("Integration Tests") {
                    steps {
                        sh("npm run test:integration")
                    }
                }
                
                stage("E2E Tests") {
                    steps {
                        sh("npm run test:e2e")
                    }
                }
            }
        }
        
        stage("Build") {
            steps {
                sh("npm run build")
                
                archiveArtifacts {
                    artifacts = "dist/**/*"
                    fingerprint = true
                }
            }
        }
        
        stage("Security Audit") {
            steps {
                sh("npm audit --audit-level=high")
                sh("npm run security:scan")
            }
        }
    }
    
    post {
        always {
            publishHTML {
                allowMissing = true
                alwaysLinkToLastBuild = true
                keepAll = true
                reportDir = "coverage/lcov-report"
                reportFiles = "index.html"
                reportName = "Test Coverage Report"
            }
        }
    }
}
```

## Pipelines con Control de Flujo

### 6. **Pipeline Condicional**

```kotlin
// conditional-pipeline.kts
pipeline {
    agent { any() }
    
    parameters {
        choice {
            name = "BRANCH_TYPE"
            choices = listOf("feature", "release", "hotfix")
            description = "Type of branch being built"
        }
    }
    
    stages {
        stage("Checkout") {
            steps {
                checkout(scm)
                script {
                    env.set("GIT_BRANCH", sh {
                        script = "git branch --show-current"
                        returnStdout = true
                    }.trim())
                    
                    echo("Building branch: ${env.GIT_BRANCH}")
                }
            }
        }
        
        stage("Build") {
            steps {
                echo("Building application...")
                sh("echo 'Building...' && sleep 2")
            }
        }
        
        stage("Feature Tests") {
            `when` {
                expression { params.getString("BRANCH_TYPE") == "feature" }
            }
            steps {
                echo("Running feature-specific tests")
                sh("echo 'Feature tests...' && sleep 1")
            }
        }
        
        stage("Integration Tests") {
            `when` {
                anyOf {
                    expression { params.getString("BRANCH_TYPE") == "release" }
                    expression { params.getString("BRANCH_TYPE") == "hotfix" }
                }
            }
            steps {
                echo("Running integration tests")
                sh("echo 'Integration tests...' && sleep 3")
            }
        }
        
        stage("Deploy to Staging") {
            `when` {
                allOf {
                    expression { params.getString("BRANCH_TYPE") != "feature" }
                    expression { 
                        val branch = env.getString("GIT_BRANCH")
                        branch.startsWith("release/") || branch.startsWith("hotfix/")
                    }
                }
            }
            steps {
                echo("Deploying to staging environment")
                sh("echo 'Deploying to staging...' && sleep 2")
            }
        }
        
        stage("Production Approval") {
            `when` {
                expression { params.getString("BRANCH_TYPE") == "release" }
            }
            steps {
                script {
                    val approval = input {
                        message = "Deploy to production?"
                        ok = "Deploy"
                        parameters = listOf(
                            choice {
                                name = "DEPLOYMENT_STRATEGY"
                                choices = listOf("rolling", "blue-green", "canary")
                                description = "Deployment strategy"
                            },
                            text {
                                name = "REASON"
                                description = "Reason for deployment"
                            }
                        )
                        submitter = "admin,devops-team"
                    }
                    
                    env.set("DEPLOYMENT_STRATEGY", approval.DEPLOYMENT_STRATEGY)
                    env.set("DEPLOYMENT_REASON", approval.REASON)
                }
            }
        }
        
        stage("Deploy to Production") {
            `when` {
                expression { env.contains("DEPLOYMENT_STRATEGY") }
            }
            steps {
                echo("Deploying to production using ${env.DEPLOYMENT_STRATEGY} strategy")
                echo("Deployment reason: ${env.DEPLOYMENT_REASON}")
                sh("echo 'Production deployment...' && sleep 5")
            }
        }
    }
    
    post {
        success {
            script {
                val branchType = params.getString("BRANCH_TYPE")
                when (branchType) {
                    "feature" -> echo("✅ Feature build completed!")
                    "release" -> echo("🚀 Release deployed successfully!")
                    "hotfix" -> echo("🔥 Hotfix deployed successfully!")
                }
            }
        }
    }
}
```

### 7. **Pipeline con Retry y Timeout**

```kotlin
// resilient-pipeline.kts
pipeline {
    agent { any() }
    
    options {
        timeout(time = 30, unit = TimeUnit.MINUTES)
        retry(2)
    }
    
    stages {
        stage("Flaky Network Operation") {
            steps {
                retry(3) {
                    timeout(time = 5, unit = TimeUnit.MINUTES) {
                        sh("""
                            echo "Attempting network operation..."
                            # Simulate flaky network call
                            if [ $((RANDOM % 3)) -eq 0 ]; then
                                echo "Network call succeeded!"
                            else
                                echo "Network call failed!"
                                exit 1
                            fi
                        """)
                    }
                }
            }
        }
        
        stage("Database Migration") {
            steps {
                timeout(time = 10, unit = TimeUnit.MINUTES) {
                    script {
                        var attempts = 0
                        val maxAttempts = 5
                        
                        while (attempts < maxAttempts) {
                            attempts++
                            try {
                                sh("echo 'Running database migration attempt $attempts'")
                                
                                // Simulate database operation
                                val exitCode = sh {
                                    script = """
                                        echo "Connecting to database..."
                                        sleep 2
                                        # Simulate success after 3 attempts
                                        if [ $attempts -ge 3 ]; then
                                            echo "Migration successful!"
                                            exit 0
                                        else
                                            echo "Migration failed, retrying..."
                                            exit 1
                                        fi
                                    """
                                    returnStatus = true
                                }
                                
                                if (exitCode == 0) {
                                    echo("✅ Database migration completed successfully!")
                                    break
                                }
                            } catch (Exception e) {
                                if (attempts >= maxAttempts) {
                                    echo("❌ Database migration failed after $maxAttempts attempts")
                                    throw e
                                }
                                echo("⚠️ Attempt $attempts failed, waiting before retry...")
                                sleep(time = attempts * 2, unit = TimeUnit.SECONDS)
                            }
                        }
                    }
                }
            }
        }
        
        stage("Health Check with Circuit Breaker") {
            steps {
                script {
                    var failures = 0
                    val maxFailures = 3
                    var circuitOpen = false
                    
                    for (service in listOf("api", "database", "cache", "queue")) {
                        if (circuitOpen) {
                            echo("🚫 Circuit breaker open, skipping $service health check")
                            continue
                        }
                        
                        try {
                            timeout(time = 30, unit = TimeUnit.SECONDS) {
                                sh("""
                                    echo "Checking health of $service..."
                                    # Simulate health check
                                    if [ "$service" = "queue" ]; then
                                        echo "Service $service is unhealthy!"
                                        exit 1
                                    else
                                        echo "Service $service is healthy ✅"
                                    fi
                                """)
                            }
                        } catch (Exception e) {
                            failures++
                            echo("❌ Health check failed for $service (failure $failures/$maxFailures)")
                            
                            if (failures >= maxFailures) {
                                echo("🚫 Circuit breaker opened due to too many failures")
                                circuitOpen = true
                            }
                        }
                    }
                    
                    if (circuitOpen) {
                        echo("⚠️ Some services are unhealthy but deployment continues")
                        currentBuild.result = "UNSTABLE"
                    } else {
                        echo("✅ All services are healthy")
                    }
                }
            }
        }
    }
    
    post {
        unstable {
            echo("⚠️ Pipeline completed with warnings")
        }
        
        failure {
            echo("💥 Pipeline failed after all retries")
        }
        
        aborted {
            echo("🛑 Pipeline was aborted due to timeout")
        }
    }
}
```

## Pipelines Paralelos

### 8. **Pipeline con Ejecución Paralela**

```kotlin
// parallel-execution.pipeline.kts
pipeline {
    agent { any() }
    
    stages {
        stage("Checkout") {
            steps {
                checkout(scm)
            }
        }
        
        stage("Parallel Build & Analysis") {
            parallel {
                stage("Build Application") {
                    agent {
                        docker { 
                            image = "gradle:7.5-jdk17"
                            reuseNode = true
                        }
                    }
                    steps {
                        echo("🔨 Building application...")
                        sh("./gradlew clean build -x test")
                        
                        stash {
                            name = "build-artifacts"
                            includes = "**/build/libs/*.jar"
                        }
                    }
                }
                
                stage("Code Quality Analysis") {
                    agent {
                        docker {
                            image = "sonarqube-scanner:latest"
                            reuseNode = true
                        }
                    }
                    steps {
                        echo("📊 Running code quality analysis...")
                        sh("sonar-scanner -Dsonar.projectKey=my-project")
                    }
                }
                
                stage("Security Scan") {
                    steps {
                        echo("🔒 Running security scan...")
                        sh("dependency-check --project my-project --scan .")
                        
                        publishHTML {
                            allowMissing = true
                            alwaysLinkToLastBuild = true
                            keepAll = true
                            reportDir = "dependency-check-report"
                            reportFiles = "dependency-check-report.html"
                            reportName = "Security Report"
                        }
                    }
                }
                
                stage("Documentation") {
                    steps {
                        echo("📚 Generating documentation...")
                        sh("./gradlew javadoc")
                        
                        publishHTML {
                            allowMissing = false
                            alwaysLinkToLastBuild = true
                            keepAll = true
                            reportDir = "build/docs/javadoc"
                            reportFiles = "index.html"
                            reportName = "API Documentation"
                        }
                    }
                }
            }
        }
        
        stage("Testing Suite") {
            parallel {
                stage("Unit Tests") {
                    steps {
                        unstash("build-artifacts")
                        echo("🧪 Running unit tests...")
                        sh("./gradlew test")
                        
                        junit {
                            testResults = "**/build/test-results/test/*.xml"
                            allowEmptyResults = true
                        }
                    }
                }
                
                stage("Integration Tests") {
                    steps {
                        unstash("build-artifacts")
                        echo("🔗 Running integration tests...")
                        sh("./gradlew integrationTest")
                        
                        junit {
                            testResults = "**/build/test-results/integrationTest/*.xml"
                            allowEmptyResults = true
                        }
                    }
                }
                
                stage("Performance Tests") {
                    steps {
                        unstash("build-artifacts")
                        echo("⚡ Running performance tests...")
                        sh("./gradlew performanceTest")
                        
                        archiveArtifacts {
                            artifacts = "build/reports/performance/**"
                            allowEmptyArchive = true
                        }
                    }
                }
                
                stage("End-to-End Tests") {
                    agent {
                        docker {
                            image = "cypress/included:latest"
                            reuseNode = true
                        }
                    }
                    steps {
                        unstash("build-artifacts")
                        echo("🌐 Running E2E tests...")
                        sh("npm run test:e2e")
                        
                        archiveArtifacts {
                            artifacts = "cypress/screenshots/**,cypress/videos/**"
                            allowEmptyArchive = true
                        }
                    }
                }
            }
        }
        
        stage("Multi-Platform Build") {
            parallel {
                stage("Linux Build") {
                    agent { label("linux") }
                    steps {
                        echo("🐧 Building for Linux...")
                        sh("./build-linux.sh")
                        archiveArtifacts("dist/linux/**")
                    }
                }
                
                stage("Windows Build") {
                    agent { label("windows") }
                    steps {
                        echo("🪟 Building for Windows...")
                        bat("build-windows.bat")
                        archiveArtifacts("dist/windows/**")
                    }
                }
                
                stage("macOS Build") {
                    agent { label("macos") }
                    steps {
                        echo("🍎 Building for macOS...")
                        sh("./build-macos.sh")
                        archiveArtifacts("dist/macos/**")
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo("📈 Collecting all reports...")
            
            // Consolidate all test results
            junit {
                testResults = "**/build/test-results/**/*.xml"
                allowEmptyResults = true
                keepLongStdio = true
            }
            
            // Archive all artifacts
            archiveArtifacts {
                artifacts = "build/libs/*.jar, dist/**"
                fingerprint = true
                allowEmptyArchive = true
            }
        }
        
        success {
            echo("🎉 All parallel stages completed successfully!")
            script {
                val duration = currentBuild.durationString
                echo("⏱️ Total build time: $duration")
            }
        }
        
        failure {
            echo("💥 One or more parallel stages failed!")
        }
    }
}
```

## Pipelines con Stash/Unstash

### 9. **Pipeline con Compartición de Artefactos**

```kotlin
// artifact-sharing.pipeline.kts
pipeline {
    agent { none() }
    
    stages {
        stage("Build") {
            agent {
                docker { 
                    image = "gradle:7.5-jdk17" 
                }
            }
            steps {
                checkout(scm)
                
                echo("🔨 Building application...")
                sh("./gradlew clean build")
                
                // Stash build artifacts for later stages
                stash {
                    name = "build-artifacts"
                    includes = "build/libs/*.jar, build/distributions/*.tar, build/distributions/*.zip"
                    excludes = "**/*-sources.jar, **/*-javadoc.jar"
                    allowEmpty = false
                }
                
                // Stash test reports
                stash {
                    name = "test-reports"
                    includes = "build/reports/tests/**, build/test-results/**"
                    allowEmpty = true
                }
                
                // Stash source code for analysis
                stash {
                    name = "source-code"
                    includes = "src/**, build.gradle.kts, gradle.properties"
                    excludes = "**/*.class"
                }
            }
        }
        
        stage("Quality Gates") {
            parallel {
                stage("Test Results Analysis") {
                    agent { any() }
                    steps {
                        unstash("test-reports")
                        
                        echo("📊 Analyzing test results...")
                        
                        junit {
                            testResults = "build/test-results/**/*.xml"
                            allowEmptyResults = true
                            keepLongStdio = true
                        }
                        
                        publishHTML {
                            allowMissing = true
                            alwaysLinkToLastBuild = true
                            keepAll = true
                            reportDir = "build/reports/tests/test"
                            reportFiles = "index.html"
                            reportName = "Test Results"
                        }
                    }
                }
                
                stage("Code Coverage") {
                    agent { 
                        docker { 
                            image = "gradle:7.5-jdk17" 
                        }
                    }
                    steps {
                        unstash("source-code")
                        unstash("test-reports")
                        
                        echo("📈 Generating code coverage report...")
                        sh("./gradlew jacocoTestReport")
                        
                        publishHTML {
                            allowMissing = false
                            alwaysLinkToLastBuild = true
                            keepAll = true
                            reportDir = "build/reports/jacoco/test/html"
                            reportFiles = "index.html"
                            reportName = "Code Coverage"
                        }
                        
                        // Check coverage threshold
                        script {
                            val coverageResult = sh {
                                script = "./gradlew jacocoTestCoverageVerification"
                                returnStatus = true
                            }
                            
                            if (coverageResult != 0) {
                                echo("⚠️ Code coverage below threshold!")
                                currentBuild.result = "UNSTABLE"
                            } else {
                                echo("✅ Code coverage meets requirements")
                            }
                        }
                    }
                }
                
                stage("Static Analysis") {
                    agent {
                        docker {
                            image = "sonarsource/sonar-scanner-cli:latest"
                        }
                    }
                    steps {
                        unstash("source-code")
                        unstash("test-reports")
                        
                        echo("🔍 Running static code analysis...")
                        
                        withCredentials(listOf(
                            string {
                                credentialsId = "sonarqube-token"
                                variable = "SONAR_TOKEN"
                            }
                        )) {
                            sh("""
                                sonar-scanner \
                                  -Dsonar.projectKey=my-awesome-project \
                                  -Dsonar.sources=src/main \
                                  -Dsonar.tests=src/test \
                                  -Dsonar.junit.reportPaths=build/test-results/test \
                                  -Dsonar.jacoco.reportPaths=build/reports/jacoco/test/jacocoTestReport.xml \
                                  -Dsonar.host.url=https://sonarqube.company.com \
                                  -Dsonar.login=$SONAR_TOKEN
                            """)
                        }
                        
                        // Wait for Quality Gate result
                        timeout(time = 5, unit = TimeUnit.MINUTES) {
                            waitForQualityGate {
                                abortPipeline = false // Don't fail the build, just mark unstable
                            }
                        }
                    }
                }
            }
        }
        
        stage("Deployment Preparation") {
            parallel {
                stage("Docker Image") {
                    agent { any() }
                    steps {
                        unstash("build-artifacts")
                        
                        echo("🐳 Building Docker image...")
                        
                        script {
                            val version = sh {
                                script = "cat VERSION.txt || echo '1.0.0-SNAPSHOT'"
                                returnStdout = true
                            }.trim()
                            
                            env.set("APP_VERSION", version)
                            
                            sh("docker build -t my-app:$version -t my-app:latest .")
                            
                            // Test the image
                            sh("docker run --rm my-app:$version java -version")
                            
                            // Save image for later use
                            sh("docker save my-app:$version | gzip > my-app-$version.tar.gz")
                            
                            stash {
                                name = "docker-image"
                                includes = "my-app-*.tar.gz"
                            }
                        }
                    }
                }
                
                stage("Helm Charts") {
                    agent { 
                        docker { 
                            image = "alpine/helm:latest" 
                        }
                    }
                    steps {
                        unstash("source-code")
                        
                        echo("⚓ Preparing Helm charts...")
                        
                        sh("helm lint charts/my-app")
                        sh("helm package charts/my-app --destination .")
                        
                        stash {
                            name = "helm-charts"
                            includes = "*.tgz"
                        }
                        
                        archiveArtifacts {
                            artifacts = "*.tgz"
                            fingerprint = true
                        }
                    }
                }
                
                stage("Infrastructure as Code") {
                    agent {
                        docker {
                            image = "hashicorp/terraform:latest"
                        }
                    }
                    steps {
                        unstash("source-code")
                        
                        echo("🏗️ Validating Terraform configuration...")
                        
                        dir("terraform") {
                            sh("terraform init")
                            sh("terraform validate")
                            sh("terraform plan -out=tfplan")
                            
                            stash {
                                name = "terraform-plan"
                                includes = "tfplan, **/*.tf"
                            }
                        }
                    }
                }
            }
        }
        
        stage("Staging Deployment") {
            agent { any() }
            steps {
                unstash("docker-image")
                unstash("helm-charts")
                
                echo("🚀 Deploying to staging environment...")
                
                script {
                    val version = env.getString("APP_VERSION")
                    
                    // Load and push Docker image
                    sh("docker load < my-app-$version.tar.gz")
                    sh("docker tag my-app:$version staging-registry.company.com/my-app:$version")
                    
                    withCredentials(listOf(
                        usernamePassword {
                            credentialsId = "staging-registry"
                            usernameVariable = "REGISTRY_USER"
                            passwordVariable = "REGISTRY_PASS"
                        }
                    )) {
                        sh("echo $REGISTRY_PASS | docker login staging-registry.company.com -u $REGISTRY_USER --password-stdin")
                        sh("docker push staging-registry.company.com/my-app:$version")
                    }
                    
                    // Deploy using Helm
                    sh("""
                        helm upgrade --install my-app-staging ./my-app-*.tgz \
                          --set image.repository=staging-registry.company.com/my-app \
                          --set image.tag=$version \
                          --set environment=staging \
                          --namespace staging \
                          --wait \
                          --timeout=600s
                    """)
                    
                    // Verify deployment
                    sh("kubectl get pods -n staging -l app=my-app")
                }
            }
        }
    }
    
    post {
        always {
            echo("🧹 Cleaning up stashes...")
            // Las stashes se limpian automáticamente al final del build
        }
        
        success {
            echo("✅ Pipeline completed successfully!")
            echo("🚀 Application deployed to staging environment")
        }
        
        unstable {
            echo("⚠️ Pipeline completed with warnings")
            echo("📊 Check quality gates and reports")
        }
        
        failure {
            echo("💥 Pipeline failed!")
            echo("📧 Sending notifications to team...")
        }
    }
}
```

## Estos ejemplos muestran:

### 🎯 **Progresión de Complejidad**
1. **Hello World** - Conceptos básicos
2. **Variables & Parámetros** - Configuración
3. **Build Pipelines** - Construcción real
4. **Control de Flujo** - Lógica condicional
5. **Resilencia** - Manejo de errores
6. **Paralelismo** - Optimización de performance  
7. **Artifact Sharing** - Colaboración entre stages

### ⚡ **Patrones Comunes**
- **Environment Management** - Variables y configuración
- **Conditional Execution** - Flujo basado en condiciones
- **Parallel Processing** - Optimización de tiempos
- **Error Handling** - Retry y tolerancia a fallos
- **Artifact Management** - Stash/unstash de archivos
- **Multi-Agent Execution** - Diferentes entornos de ejecución

### 🛠️ **Casos de Uso Reales**
- Builds de Java/Gradle y Node.js
- Pipelines de CI/CD completos
- Testing en paralelo
- Deployments condicionales
- Quality gates y análisis de código
- Multi-platform builds

Estos ejemplos proporcionan una base sólida para empezar con Hodei Pipeline DSL.