# Ejemplos de Integración - Hodei Pipeline DSL

## Integración con Sistemas Existentes

### 1. **Integración con Jenkins**

Migración gradual desde Jenkins existente:

```kotlin
// jenkins-migration.pipeline.kts
pipeline {
    agent { any() }
    
    // Mantener compatibilidad con variables Jenkins existentes
    environment {
        set("WORKSPACE", env("WORKSPACE") ?: "/workspace")
        set("BUILD_NUMBER", env("BUILD_NUMBER") ?: "1")
        set("JOB_NAME", env("JOB_NAME") ?: "migration-job")
        set("BUILD_URL", env("BUILD_URL") ?: "http://localhost/build/1")
    }
    
    parameters {
        // Parámetros idénticos a Jenkins job existente
        choice {
            name = "ENVIRONMENT"
            choices = listOf("dev", "staging", "prod")
            description = "Deployment environment"
        }
        
        string {
            name = "VERSION"
            defaultValue = "latest"
            description = "Application version to deploy"
        }
    }
    
    stages {
        stage("Jenkins Compatibility Check") {
            steps {
                echo("🔄 Running in compatibility mode with Jenkins")
                
                script {
                    // Verificar variables Jenkins están disponibles
                    val jenkinsVars = listOf("WORKSPACE", "BUILD_NUMBER", "JOB_NAME")
                    
                    jenkinsVars.forEach { varName ->
                        val value = env.getString(varName)
                        echo("✅ $varName = $value")
                    }
                    
                    // Simular comportamiento Jenkins
                    if (env.contains("JENKINS_URL")) {
                        echo("🏗️ Running on Jenkins: ${env.JENKINS_URL}")
                    } else {
                        echo("🏗️ Running on Hodei Pipeline DSL (Jenkins-compatible mode)")
                    }
                }
            }
        }
        
        stage("Call Existing Jenkins Job") {
            steps {
                echo("📞 Calling existing Jenkins downstream job...")
                
                script {
                    // Llamar a job Jenkins existente durante migración
                    val buildResult = build {
                        job = "legacy-integration-tests"
                        parameters = mapOf(
                            "VERSION" to params.getString("VERSION"),
                            "ENVIRONMENT" to params.getString("ENVIRONMENT")
                        )
                        wait = true
                        propagate = true
                    }
                    
                    echo("✅ Legacy job completed with result: ${buildResult.result}")
                    
                    // Copiar artefactos del job legacy
                    copyArtifacts {
                        projectName = "legacy-integration-tests"
                        buildSelector = specific(buildResult.number)
                        target = "legacy-artifacts/"
                        flatten = false
                    }
                }
            }
        }
        
        stage("Modern Hodei Steps") {
            steps {
                echo("🚀 Using modern Hodei Pipeline DSL features...")
                
                // Aprovechar características modernas de Hodei
                parallel(mapOf(
                    "Static Analysis" to {
                        sh("sonar-scanner -Dsonar.projectKey=migrated-project")
                    },
                    "Security Scan" to {
                        sh("security-scanner --format json --output security-report.json")
                    },
                    "Performance Test" to {
                        sh("k6 run performance-test.js")
                    }
                ))
            }
        }
        
        stage("Artifact Compatibility") {
            steps {
                echo("📦 Ensuring artifact compatibility...")
                
                // Mantener estructura de artefactos compatible con Jenkins
                archiveArtifacts {
                    artifacts = "build/libs/*.jar, legacy-artifacts/**"
                    fingerprint = true
                    allowEmptyArchive = false
                }
                
                // Publicar resultados en formato Jenkins
                junit {
                    testResults = "**/target/surefire-reports/*.xml"
                    allowEmptyResults = true
                    keepLongStdio = true
                }
                
                publishHTML {
                    allowMissing = false
                    alwaysLinkToLastBuild = true
                    keepAll = true
                    reportDir = "build/reports/coverage"
                    reportFiles = "index.html"
                    reportName = "Coverage Report"
                }
            }
        }
    }
    
    post {
        always {
            // Notificaciones compatibles con Jenkins
            emailext {
                to = "\$DEFAULT_RECIPIENTS"
                subject = "Build \${currentBuild.result}: \${env.JOB_NAME} - \${env.BUILD_NUMBER}"
                body = """
                    Build: \${env.BUILD_URL}
                    Result: \${currentBuild.result}
                    Duration: \${currentBuild.durationString}
                """.trimIndent()
                attachLog = true
            }
        }
        
        success {
            echo("✅ Migration pipeline completed successfully!")
            
            // Trigger downstream Jenkins jobs si existen
            script {
                if (params.getString("ENVIRONMENT") == "prod") {
                    build {
                        job = "legacy-production-deployment"
                        parameters = mapOf(
                            "VERSION" to params.getString("VERSION"),
                            "ARTIFACTS_BUILD" to env.getString("BUILD_NUMBER")
                        )
                        wait = false
                    }
                }
            }
        }
    }
}
```

### 2. **Integración con GitLab CI**

Uso de Hodei como replacement de GitLab CI:

```kotlin
// gitlab-integration.pipeline.kts
pipeline {
    agent { any() }
    
    // Variables equivalentes a GitLab CI
    environment {
        set("CI", "true")
        set("CI_COMMIT_SHA", env("GIT_COMMIT") ?: "unknown")
        set("CI_COMMIT_REF_NAME", env("GIT_BRANCH") ?: "main")
        set("CI_PROJECT_NAME", "hodei-integration-project")
        set("CI_PIPELINE_ID", env("BUILD_NUMBER") ?: "1")
        set("CI_JOB_URL", env("BUILD_URL") ?: "")
        set("GITLAB_CI", "true") // Para compatibilidad con scripts existentes
    }
    
    stages {
        stage("GitLab Variables Compatibility") {
            steps {
                echo("🦊 GitLab CI compatibility mode enabled")
                
                script {
                    // Mapear variables GitLab CI a Hodei
                    val gitlabVars = mapOf(
                        "CI_COMMIT_SHA" to env.getString("GIT_COMMIT"),
                        "CI_COMMIT_REF_NAME" to env.getString("GIT_BRANCH"),
                        "CI_PROJECT_NAME" to env.getString("JOB_NAME"),
                        "CI_PIPELINE_ID" to env.getString("BUILD_NUMBER")
                    )
                    
                    gitlabVars.forEach { (name, value) ->
                        echo("📋 $name = $value")
                        env.set(name, value)
                    }
                    
                    // Detectar merge requests
                    val isMR = env.getString("CHANGE_ID") != null
                    env.set("CI_MERGE_REQUEST_ID", env.getString("CHANGE_ID") ?: "")
                    env.set("CI_MERGE_REQUEST_TARGET_BRANCH", env.getString("CHANGE_TARGET") ?: "")
                    
                    if (isMR) {
                        echo("🔀 Merge Request detected: ${env.CI_MERGE_REQUEST_ID}")
                        echo("🎯 Target branch: ${env.CI_MERGE_REQUEST_TARGET_BRANCH}")
                    }
                }
            }
        }
        
        stage("Build") {
            // Equivalente a job 'build' en GitLab CI
            steps {
                echo("🔨 Build stage (GitLab equivalent)")
                
                // Usar imagen Docker como en GitLab CI
                script {
                    docker.image("gradle:7.5-jdk17").inside("-v gradle-cache:/home/gradle/.gradle") {
                        sh("./gradlew clean build")
                        
                        // Artifacts para siguientes stages (equivalente a GitLab artifacts)
                        stash {
                            name = "build-artifacts"
                            includes = "build/libs/*.jar, build/reports/**"
                        }
                    }
                }
            }
        }
        
        stage("Test") {
            parallel {
                stage("Unit Tests") {
                    steps {
                        unstash("build-artifacts")
                        
                        script {
                            docker.image("gradle:7.5-jdk17").inside {
                                sh("./gradlew test")
                                
                                // Publicar cobertura como en GitLab CI
                                publishHTML {
                                    allowMissing = false
                                    alwaysLinkToLastBuild = true
                                    keepAll = true
                                    reportDir = "build/reports/jacoco/test/html"
                                    reportFiles = "index.html"
                                    reportName = "Coverage Report"
                                }
                            }
                        }
                    }
                }
                
                stage("Integration Tests") {
                    steps {
                        unstash("build-artifacts")
                        
                        script {
                            // Servicios auxiliares como en GitLab CI
                            docker.image("postgres:13").withRun("-e POSTGRES_PASSWORD=test") { postgres ->
                                docker.image("redis:6").withRun() { redis ->
                                    docker.image("gradle:7.5-jdk17").inside("--link ${postgres.id}:postgres --link ${redis.id}:redis") {
                                        sh("""
                                            export DATABASE_URL=jdbc:postgresql://postgres:5432/test
                                            export REDIS_URL=redis://redis:6379
                                            ./gradlew integrationTest
                                        """)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        stage("Security Scan") {
            // Equivalente a security scanning en GitLab CI
            steps {
                script {
                    docker.image("securecodewarrior/gitlab-sast:latest").inside {
                        sh("gitlab-sast-analyzer --format json --output security-report.json")
                        
                        archiveArtifacts {
                            artifacts = "security-report.json"
                            allowEmptyArchive = true
                        }
                        
                        // Parsear reporte de seguridad
                        val securityReport = readJSON(file = "security-report.json")
                        val vulnerabilities = securityReport.vulnerabilities?.size ?: 0
                        
                        echo("🔒 Security scan completed: $vulnerabilities vulnerabilities found")
                        
                        if (vulnerabilities > 0) {
                            currentBuild.result = "UNSTABLE"
                        }
                    }
                }
            }
        }
        
        stage("Deploy to Staging") {
            when {
                branch("main")
                not { changeRequest() }
            }
            environment {
                set("DEPLOY_ENVIRONMENT", "staging")
                set("KUBE_NAMESPACE", "app-staging")
            }
            steps {
                echo("🚀 Deploying to staging (GitLab CD equivalent)")
                
                unstash("build-artifacts")
                
                script {
                    // Usar kubectl como en GitLab CI
                    docker.image("bitnami/kubectl:latest").inside {
                        withCredentials(listOf(
                            kubeconfigFile {
                                credentialsId = "k8s-staging-config"
                                variable = "KUBECONFIG"
                            }
                        )) {
                            sh("""
                                kubectl set image deployment/app \
                                  app=myregistry/app:\${CI_COMMIT_SHA} \
                                  --namespace=\${KUBE_NAMESPACE}
                                
                                kubectl rollout status deployment/app \
                                  --namespace=\${KUBE_NAMESPACE} \
                                  --timeout=300s
                            """)
                        }
                    }
                }
            }
        }
        
        stage("Manual Deploy to Production") {
            when {
                branch("main")
            }
            steps {
                script {
                    // Manual deployment como en GitLab CI
                    timeout(time = 24, unit = TimeUnit.HOURS) {
                        input {
                            message = "Deploy to production?"
                            ok = "Deploy"
                            submitter = "devops-team"
                            parameters = listOf(
                                choice {
                                    name = "DEPLOYMENT_STRATEGY"
                                    choices = listOf("rolling", "blue-green")
                                    description = "Deployment strategy"
                                }
                            )
                        }
                    }
                    
                    echo("🚀 Deploying to production...")
                    
                    docker.image("bitnami/kubectl:latest").inside {
                        withCredentials(listOf(
                            kubeconfigFile {
                                credentialsId = "k8s-production-config"
                                variable = "KUBECONFIG"
                            }
                        )) {
                            sh("""
                                kubectl set image deployment/app \
                                  app=myregistry/app:\${CI_COMMIT_SHA} \
                                  --namespace=app-production
                                
                                kubectl rollout status deployment/app \
                                  --namespace=app-production \
                                  --timeout=600s
                            """)
                        }
                    }
                }
            }
        }
    }
    
    post {
        always {
            // Cleanup similar a GitLab CI after_script
            echo("🧹 Cleanup phase")
            
            script {
                // Limpiar recursos Docker
                sh("docker system prune -f")
                
                // Limpiar workspace grandes
                if (directorySize(".") > 1000000000) { // 1GB
                    echo("⚠️ Large workspace detected, cleaning up...")
                    sh("find . -name '*.log' -size +100M -delete")
                    sh("find . -name 'node_modules' -exec rm -rf {} +")
                }
            }
        }
        
        success {
            echo("✅ Pipeline completed successfully!")
            
            // Notificaciones como GitLab CI
            script {
                if (env.contains("SLACK_WEBHOOK")) {
                    slackSend {
                        channel = "#deployments"
                        color = "good"
                        message = """
                            ✅ Pipeline Success
                            Project: ${env.CI_PROJECT_NAME}
                            Branch: ${env.CI_COMMIT_REF_NAME}
                            Commit: ${env.CI_COMMIT_SHA.take(8)}
                            Pipeline: ${env.CI_PIPELINE_ID}
                        """.trimIndent()
                        webhookUrl = env.getString("SLACK_WEBHOOK")
                    }
                }
            }
        }
        
        failure {
            echo("💥 Pipeline failed!")
            
            // Rollback automático en caso de fallo en producción
            script {
                if (env.getString("DEPLOY_ENVIRONMENT") == "production") {
                    echo("🔄 Initiating automatic rollback...")
                    
                    docker.image("bitnami/kubectl:latest").inside {
                        withCredentials(listOf(
                            kubeconfigFile {
                                credentialsId = "k8s-production-config"
                                variable = "KUBECONFIG"
                            }
                        )) {
                            sh("kubectl rollout undo deployment/app --namespace=app-production")
                        }
                    }
                }
            }
        }
    }
}
```

### 3. **Integración con GitHub Actions**

Replacement de GitHub Actions workflows:

```kotlin
// github-actions-integration.pipeline.kts
pipeline {
    agent { any() }
    
    // Variables equivalentes a GitHub Actions
    environment {
        set("GITHUB_ACTIONS", "true")
        set("GITHUB_WORKSPACE", env("WORKSPACE") ?: "/workspace")
        set("GITHUB_SHA", env("GIT_COMMIT") ?: "")
        set("GITHUB_REF", "refs/heads/${env("GIT_BRANCH") ?: "main"}")
        set("GITHUB_REPOSITORY", "company/hodei-integration")
        set("GITHUB_RUN_ID", env("BUILD_NUMBER") ?: "1")
        set("GITHUB_RUN_NUMBER", env("BUILD_NUMBER") ?: "1")
        set("RUNNER_OS", "Linux")
        set("RUNNER_ARCH", "X64")
    }
    
    triggers {
        // Equivalente a GitHub Actions triggers
        githubPush()
        pullRequest {
            branches = listOf("main", "develop")
            types = listOf("opened", "synchronize", "reopened")
        }
    }
    
    stages {
        stage("Checkout") {
            // Equivalente a actions/checkout
            steps {
                echo("📥 Checking out code (GitHub Actions equivalent)")
                checkout(scm)
                
                script {
                    // Información del contexto como en GitHub Actions
                    echo("📋 GitHub Actions Context:")
                    echo("  Repository: ${env.GITHUB_REPOSITORY}")
                    echo("  SHA: ${env.GITHUB_SHA}")
                    echo("  Ref: ${env.GITHUB_REF}")
                    echo("  Run ID: ${env.GITHUB_RUN_ID}")
                    
                    // Detectar evento que disparó el pipeline
                    val eventName = if (env.contains("CHANGE_ID")) "pull_request" else "push"
                    env.set("GITHUB_EVENT_NAME", eventName)
                    
                    echo("  Event: ${eventName}")
                    
                    if (eventName == "pull_request") {
                        env.set("GITHUB_HEAD_REF", env.getString("CHANGE_BRANCH"))
                        env.set("GITHUB_BASE_REF", env.getString("CHANGE_TARGET"))
                        echo("  PR: ${env.CHANGE_BRANCH} -> ${env.CHANGE_TARGET}")
                    }
                }
            }
        }
        
        stage("Setup") {
            parallel {
                stage("Setup Node.js") {
                    // Equivalente a actions/setup-node
                    steps {
                        script {
                            docker.image("node:18").inside {
                                sh("node --version")
                                sh("npm --version")
                                
                                // Cache npm dependencies
                                cache {
                                    key = "npm-cache-\${hashFiles('package-lock.json')}"
                                    paths = listOf("~/.npm")
                                    action = {
                                        sh("npm ci")
                                    }
                                }
                            }
                        }
                    }
                }
                
                stage("Setup Java") {
                    // Equivalente a actions/setup-java
                    steps {
                        script {
                            docker.image("openjdk:17").inside {
                                sh("java -version")
                                
                                // Setup Gradle
                                cache {
                                    key = "gradle-cache-\${hashFiles('**/*.gradle*')}"
                                    paths = listOf("~/.gradle/caches", "~/.gradle/wrapper")
                                    action = {
                                        sh("./gradlew dependencies")
                                    }
                                }
                            }
                        }
                    }
                }
                
                stage("Setup Python") {
                    // Equivalente a actions/setup-python
                    steps {
                        script {
                            docker.image("python:3.9").inside {
                                sh("python --version")
                                sh("pip --version")
                                
                                // Cache pip dependencies
                                cache {
                                    key = "pip-cache-\${hashFiles('requirements.txt')}"
                                    paths = listOf("~/.cache/pip")
                                    action = {
                                        sh("pip install -r requirements.txt")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        stage("Build and Test") {
            matrix {
                axes {
                    axis {
                        name = "os"
                        values = listOf("ubuntu-latest", "windows-latest", "macos-latest")
                    }
                    axis {
                        name = "node-version"
                        values = listOf("16", "18", "20")
                    }
                }
                
                excludes = listOf(
                    mapOf("os" to "windows-latest", "node-version" to "16")
                )
            }
            
            steps {
                script {
                    val os = matrix.getString("os")
                    val nodeVersion = matrix.getString("node-version")
                    
                    echo("🏗️ Building on $os with Node.js $nodeVersion")
                    
                    // Seleccionar imagen basada en OS
                    val dockerImage = when (os) {
                        "ubuntu-latest" -> "node:$nodeVersion"
                        "windows-latest" -> "node:$nodeVersion-windowsservercore"
                        "macos-latest" -> "node:$nodeVersion" // Usar Docker for Mac
                        else -> "node:$nodeVersion"
                    }
                    
                    docker.image(dockerImage).inside {
                        // Build steps
                        sh("npm run build")
                        sh("npm run test")
                        
                        // Upload artifacts equivalente
                        if (os == "ubuntu-latest" && nodeVersion == "18") {
                            archiveArtifacts {
                                artifacts = "dist/**, coverage/**"
                                name = "build-artifacts-$os-node$nodeVersion"
                            }
                        }
                    }
                }
            }
        }
        
        stage("Code Quality") {
            when {
                expression { env.getString("GITHUB_EVENT_NAME") == "pull_request" }
            }
            steps {
                echo("🔍 Code quality checks for PR")
                
                parallel(mapOf(
                    "ESLint" to {
                        docker.image("node:18").inside {
                            sh("npm run lint")
                            
                            // Equivalente a dorny/test-reporter
                            publishHTML {
                                allowMissing = true
                                alwaysLinkToLastBuild = false
                                keepAll = false
                                reportDir = "eslint-report"
                                reportFiles = "index.html"
                                reportName = "ESLint Report"
                            }
                        }
                    },
                    
                    "Type Check" to {
                        docker.image("node:18").inside {
                            sh("npm run type-check")
                        }
                    },
                    
                    "Security Audit" to {
                        docker.image("node:18").inside {
                            sh("npm audit --audit-level=high")
                        }
                    }
                ))
            }
        }
        
        stage("Deploy Preview") {
            when {
                expression { env.getString("GITHUB_EVENT_NAME") == "pull_request" }
            }
            steps {
                echo("🚀 Deploying PR preview")
                
                script {
                    val prNumber = env.getString("CHANGE_ID")
                    val previewUrl = "https://preview-pr-$prNumber.company.com"
                    
                    // Deploy to preview environment
                    sh("""
                        deploy-preview \
                          --pr-number $prNumber \
                          --build-dir ./dist \
                          --url $previewUrl
                    """)
                    
                    env.set("PREVIEW_URL", previewUrl)
                    
                    // Comentar en el PR con URL de preview
                    githubComment {
                        repository = env.getString("GITHUB_REPOSITORY")
                        issueNumber = prNumber.toInt()
                        body = """
                            ## 🚀 Preview Deployed
                            
                            Preview URL: [$previewUrl]($previewUrl)
                            
                            Built from commit: ${env.GITHUB_SHA.take(7)}
                        """.trimIndent()
                        token = credentials("github-token")
                    }
                }
            }
        }
        
        stage("E2E Tests") {
            when {
                expression { env.contains("PREVIEW_URL") || env.getString("GITHUB_REF") == "refs/heads/main" }
            }
            steps {
                echo("🌐 Running E2E tests")
                
                script {
                    val testUrl = env.getString("PREVIEW_URL") ?: "https://staging.company.com"
                    
                    docker.image("mcr.microsoft.com/playwright:focal").inside {
                        sh("""
                            export BASE_URL=$testUrl
                            npm run test:e2e
                        """)
                        
                        // Upload test results y screenshots
                        archiveArtifacts {
                            artifacts = "test-results/**, playwright-report/**"
                            allowEmptyArchive = true
                        }
                        
                        publishHTML {
                            allowMissing = true
                            alwaysLinkToLastBuild = true
                            keepAll = true
                            reportDir = "playwright-report"
                            reportFiles = "index.html"
                            reportName = "Playwright Report"
                        }
                    }
                }
            }
        }
        
        stage("Release") {
            when {
                allOf {
                    branch("main")
                    not { changeRequest() }
                }
            }
            steps {
                echo("🏷️ Creating release")
                
                script {
                    // Equivalent to actions/create-release
                    val version = sh {
                        script = "node -p \"require('./package.json').version\""
                        returnStdout = true
                    }.trim()
                    
                    val tagName = "v$version"
                    
                    // Create Git tag
                    sh("""
                        git tag $tagName ${env.GITHUB_SHA}
                        git push origin $tagName
                    """)
                    
                    // Create GitHub release
                    githubRelease {
                        repository = env.getString("GITHUB_REPOSITORY")
                        tagName = tagName
                        name = "Release $version"
                        body = generateReleaseNotes(version)
                        draft = false
                        prerelease = version.contains("alpha") || version.contains("beta")
                        token = credentials("github-token")
                        
                        // Upload release assets
                        assets = listOf(
                            "dist/app.tar.gz",
                            "dist/checksums.txt"
                        )
                    }
                    
                    echo("✅ Release $tagName created successfully")
                }
            }
        }
        
        stage("Deploy Production") {
            when {
                tag("v*")
            }
            steps {
                echo("🚀 Deploying to production")
                
                script {
                    val version = env.getString("TAG_NAME").removePrefix("v")
                    
                    // Production deployment
                    sh("""
                        kubectl set image deployment/app \
                          app=myregistry/app:$version \
                          --namespace=production
                        
                        kubectl rollout status deployment/app \
                          --namespace=production \
                          --timeout=600s
                    """)
                    
                    // Update deployment status en GitHub
                    githubDeploymentStatus {
                        repository = env.getString("GITHUB_REPOSITORY")
                        deploymentId = env.getString("GITHUB_DEPLOYMENT_ID")
                        state = "success"
                        environmentUrl = "https://app.company.com"
                        description = "Deployed version $version to production"
                        token = credentials("github-token")
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo("📊 Uploading test results")
            
            // Equivalent to test reporting actions
            junit {
                testResults = "**/junit.xml, **/test-results.xml"
                allowEmptyResults = true
            }
            
            // Coverage reporting
            publishHTML {
                allowMissing = true
                alwaysLinkToLastBuild = true
                keepAll = true
                reportDir = "coverage"
                reportFiles = "index.html"
                reportName = "Coverage Report"
            }
        }
        
        success {
            script {
                if (env.getString("GITHUB_EVENT_NAME") == "pull_request") {
                    // Update PR with success status
                    githubStatus {
                        repository = env.getString("GITHUB_REPOSITORY")
                        sha = env.getString("GITHUB_SHA")
                        state = "success"
                        context = "hodei-pipeline"
                        description = "All checks passed!"
                        targetUrl = env.getString("BUILD_URL")
                        token = credentials("github-token")
                    }
                    
                    // Auto-merge si todas las condiciones se cumplen
                    if (pullRequestCanAutoMerge()) {
                        githubMerge {
                            repository = env.getString("GITHUB_REPOSITORY")
                            pullNumber = env.getString("CHANGE_ID").toInt()
                            mergeMethod = "squash"
                            token = credentials("github-token")
                        }
                    }
                }
            }
        }
        
        failure {
            script {
                if (env.getString("GITHUB_EVENT_NAME") == "pull_request") {
                    // Update PR with failure status
                    githubStatus {
                        repository = env.getString("GITHUB_REPOSITORY")
                        sha = env.getString("GITHUB_SHA")
                        state = "failure"
                        context = "hodei-pipeline"
                        description = "Some checks failed"
                        targetUrl = env.getString("BUILD_URL")
                        token = credentials("github-token")
                    }
                    
                    // Comment on PR with failure details
                    githubComment {
                        repository = env.getString("GITHUB_REPOSITORY")
                        issueNumber = env.getString("CHANGE_ID").toInt()
                        body = """
                            ## ❌ Pipeline Failed
                            
                            The pipeline failed during execution.
                            
                            **Build**: [#${env.BUILD_NUMBER}](${env.BUILD_URL})
                            **Commit**: ${env.GITHUB_SHA.take(7)}
                            
                            Please check the logs for more details.
                        """.trimIndent()
                        token = credentials("github-token")
                    }
                }
            }
        }
    }
}

// Helper functions
fun generateReleaseNotes(version: String): String {
    return """
        ## What's New in $version
        
        ### Features
        - Improved performance and reliability
        - Enhanced user experience
        - Bug fixes and security updates
        
        ### Technical Details
        - Built from commit: ${env.GITHUB_SHA}
        - Build number: ${env.GITHUB_RUN_NUMBER}
        
        **Full Changelog**: https://github.com/${env.GITHUB_REPOSITORY}/compare/v1.0.0...$version
    """.trimIndent()
}

fun pullRequestCanAutoMerge(): Boolean {
    // Logic to determine if PR can be auto-merged
    // Check for required reviews, status checks, etc.
    
    val requiredChecks = listOf("build", "test", "lint", "security")
    val passedChecks = getCurrentPRChecks()
    
    return requiredChecks.all { it in passedChecks } && 
           hasRequiredApprovals() && 
           !hasConflicts()
}
```

### 4. **Integración con Azure DevOps**

```kotlin
// azure-devops-integration.pipeline.kts
pipeline {
    agent { any() }
    
    // Variables Azure DevOps
    environment {
        set("BUILD_BUILDNUMBER", env("BUILD_NUMBER") ?: "1")
        set("BUILD_SOURCEBRANCH", "refs/heads/${env("GIT_BRANCH") ?: "main"}")
        set("BUILD_SOURCEVERSION", env("GIT_COMMIT") ?: "")
        set("BUILD_REPOSITORY_NAME", "hodei-integration")
        set("SYSTEM_TEAMPROJECT", "MyProject")
        set("AGENT_OS", "Linux")
        set("AGENT_JOBNAME", env("JOB_NAME") ?: "Build")
    }
    
    parameters {
        // Equivalente a runtime parameters en Azure DevOps
        string {
            name = "configuration"
            defaultValue = "Release"
            description = "Build configuration"
        }
        
        choice {
            name = "platform"
            choices = listOf("Any CPU", "x64", "x86")
            description = "Target platform"
        }
    }
    
    stages {
        stage("Azure DevOps Setup") {
            steps {
                echo("⚡ Azure DevOps compatibility mode")
                
                script {
                    // Task equivalente a PublishBuildArtifacts
                    env.set("ARTIFACT_STAGING_DIRECTORY", "${env.WORKSPACE}/artifacts")
                    sh("mkdir -p ${env.ARTIFACT_STAGING_DIRECTORY}")
                    
                    // Sistema de variables Azure DevOps
                    val buildVars = mapOf(
                        "BUILD_BUILDNUMBER" to env.getString("BUILD_NUMBER"),
                        "BUILD_SOURCEBRANCH" to env.getString("GIT_BRANCH"),
                        "BUILD_SOURCEVERSION" to env.getString("GIT_COMMIT"),
                        "SYSTEM_TEAMPROJECT" to env.getString("SYSTEM_TEAMPROJECT")
                    )
                    
                    buildVars.forEach { (name, value) ->
                        echo("##vso[task.setvariable variable=$name]$value")
                    }
                }
            }
        }
        
        stage("NuGet Restore") {
            // Equivalente a NuGetCommand@2
            steps {
                script {
                    docker.image("mcr.microsoft.com/dotnet/sdk:6.0").inside {
                        sh("dotnet restore MyProject.sln --verbosity normal")
                        
                        // Azure DevOps logging
                        echo("##[section]NuGet packages restored successfully")
                    }
                }
            }
        }
        
        stage("Build Solution") {
            // Equivalente a VSBuild@1
            steps {
                script {
                    docker.image("mcr.microsoft.com/dotnet/sdk:6.0").inside {
                        val configuration = params.getString("configuration")
                        val platform = params.getString("platform")
                        
                        sh("""
                            dotnet build MyProject.sln \
                              --configuration $configuration \
                              --no-restore \
                              --verbosity normal
                        """)
                        
                        // Copy artifacts to staging directory
                        sh("cp -r bin/${configuration}/* ${env.ARTIFACT_STAGING_DIRECTORY}/")
                        
                        echo("##[section]Build completed successfully")
                    }
                }
            }
        }
        
        stage("Run Tests") {
            // Equivalente a VSTest@2
            steps {
                script {
                    docker.image("mcr.microsoft.com/dotnet/sdk:6.0").inside {
                        sh("""
                            dotnet test MyProject.sln \
                              --configuration ${params.configuration} \
                              --no-build \
                              --logger trx \
                              --collect:"XPlat Code Coverage"
                        """)
                        
                        // Publish test results (equivalente a PublishTestResults@2)
                        publishHTML {
                            allowMissing = true
                            alwaysLinkToLastBuild = true
                            keepAll = true
                            reportDir = "TestResults"
                            reportFiles = "coverage.html"
                            reportName = "Code Coverage"
                        }
                        
                        junit {
                            testResults = "**/TestResults/*.trx"
                            allowEmptyResults = true
                        }
                        
                        echo("##[section]Test execution completed")
                    }
                }
            }
        }
        
        stage("Publish Artifacts") {
            // Equivalente a PublishBuildArtifacts@1
            steps {
                archiveArtifacts {
                    artifacts = "artifacts/**"
                    name = "drop"
                    fingerprint = true
                }
                
                echo("##[section]Build artifacts published")
            }
        }
        
        stage("Deploy to Dev") {
            // Equivalente a Azure App Service deploy task
            when {
                branch("develop")
            }
            steps {
                script {
                    withCredentials(listOf(
                        azureServicePrincipal {
                            credentialsId = "azure-service-principal"
                            subscriptionIdVariable = "AZURE_SUBSCRIPTION_ID"
                            clientIdVariable = "AZURE_CLIENT_ID"
                            clientSecretVariable = "AZURE_CLIENT_SECRET"
                            tenantIdVariable = "AZURE_TENANT_ID"
                        }
                    )) {
                        docker.image("mcr.microsoft.com/azure-cli:latest").inside {
                            sh("""
                                az login --service-principal \
                                  -u \$AZURE_CLIENT_ID \
                                  -p \$AZURE_CLIENT_SECRET \
                                  --tenant \$AZURE_TENANT_ID
                                
                                az webapp deployment source config-zip \
                                  --resource-group myResourceGroup \
                                  --name myWebApp-dev \
                                  --src artifacts/app.zip
                            """)
                        }
                    }
                }
                
                echo("##[section]Deployed to development environment")
            }
        }
    }
    
    post {
        always {
            // Equivalente a task condition "Always"
            echo("##[section]Pipeline cleanup")
            
            script {
                // Azure DevOps build summary
                echo("""
                    ##[section]Build Summary
                    Build Number: ${env.BUILD_BUILDNUMBER}
                    Source Branch: ${env.BUILD_SOURCEBRANCH}
                    Source Version: ${env.BUILD_SOURCEVERSION}
                    Configuration: ${params.configuration}
                    Platform: ${params.platform}
                """.trimIndent())
            }
        }
        
        success {
            echo("##[section]Pipeline succeeded")
            
            // Azure DevOps work item update
            script {
                if (env.contains("SYSTEM_ACCESSTOKEN")) {
                    azureDevOpsWorkItemUpdate {
                        organization = "myorg"
                        project = env.getString("SYSTEM_TEAMPROJECT")
                        workItemId = extractWorkItemFromCommit()
                        state = "Resolved"
                        comment = "Fixed in build ${env.BUILD_BUILDNUMBER}"
                        token = credentials("azure-devops-token")
                    }
                }
            }
        }
        
        failure {
            echo("##[error]Pipeline failed")
            
            // Azure DevOps notification
            script {
                azureDevOpsNotification {
                    message = "Build ${env.BUILD_BUILDNUMBER} failed"
                    team = "development-team"
                    priority = "high"
                }
            }
        }
    }
}
```

Estos ejemplos de integración demuestran:

### 🔄 **Compatibilidad Total**
- **Variables de entorno** equivalentes a cada plataforma
- **Sintaxis familiar** para equipos migrando
- **Funcionalidad idéntica** a plataformas originales
- **Artefactos compatibles** con sistemas existentes

### 🚀 **Mejoras Modernas**
- **Type-safety** que las plataformas originales no tienen
- **Coroutines** para paralelismo real vs. simulado
- **Plugin system** extensible dinámicamente
- **Error handling** robusto con retry automático

### 🛠️ **Facilidad de Migración**
- **Drop-in replacement** sin cambios de workflow
- **Migración gradual** manteniendo sistemas existentes
- **Compatibilidad bidireccional** para transiciones suaves
- **Scripts de migración** automáticos incluidos