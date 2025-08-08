# Especificación del DSL - Hodei Pipeline DSL

## Resumen Ejecutivo

El DSL (Domain Specific Language) de Hodei Pipeline proporciona una **sintaxis declarativa type-safe** en Kotlin que replica completamente la funcionalidad de **Jenkins Declarative Pipeline** con mejoras modernas como **coroutines**, **type safety** y **extensibilidad via plugins**.

## Principios de Diseño del DSL

### 🎯 **Type-Safety First**
- Validación en tiempo de compilación
- Autocompletado completo en IDEs
- Refactoring seguro automático
- Detección temprana de errores

### 🔄 **Jenkins Syntax Compatibility**
- Sintaxis idéntica a Jenkins Declarative Pipeline
- Migración directa sin cambios de código
- Mismos nombres de métodos y parámetros
- Comportamiento equivalente garantizado

### 🚀 **Modern Kotlin Features**
- Coroutines para concurrencia real
- DSL markers para scope safety
- Extension functions para extensibilidad
- Sealed classes para type safety

### 🔌 **Plugin Extensibility**
- DSL dinámico generado por plugins
- Extension points claramente definidos
- Type-safe plugin configurations
- Runtime DSL generation

## Sintaxis Core del DSL

### 1. **Pipeline Declaration**

#### Estructura Básica
```kotlin
// pipeline.kts
pipeline {
    // Configuración global del pipeline
    agent {
        label("linux")
    }
    
    environment {
        set("JAVA_HOME", "/usr/lib/jvm/java-17")
        set("GRADLE_OPTS", "-Xmx2g")
    }
    
    stages {
        stage("Build") {
            steps {
                sh("./gradlew build")
            }
        }
    }
    
    post {
        always {
            cleanWs()
        }
    }
}
```

#### Sintaxis Completa con Opciones
```kotlin
pipeline {
    // === CONFIGURACIÓN GLOBAL ===
    
    // Agente por defecto (opcional)
    agent {
        any()                           // Cualquier agente disponible
        none()                          // Sin agente por defecto
        label("linux && docker")        // Por etiquetas
        docker {                        // Contenedor Docker
            image = "gradle:7.5-jdk17"
            args = "-v /var/run/docker.sock:/var/run/docker.sock"
        }
        kubernetes {                    // Pod de Kubernetes
            yaml = "pod-template.yaml"
        }
    }
    
    // Variables de entorno globales
    environment {
        set("BUILD_TYPE", "release")
        set("VERSION", env("BUILD_NUMBER") ?: "1.0.0-SNAPSHOT")
        credentials("API_KEY", "secret-api-key")  // Desde credentials store
        file("CONFIG_FILE", "config-file-id")     // Archivo desde credentials
    }
    
    // Opciones del pipeline
    options {
        buildDiscarder {
            logRotator {
                daysToKeep = 30
                numToKeep = 10
            }
        }
        timeout(time = 60, unit = TimeUnit.MINUTES)
        retry(3)
        skipDefaultCheckout()
        checkoutToSubdirectory("src")
        preserveStashes(buildCount = 5)
        parallelsAlwaysFailFast()
    }
    
    // Parámetros del pipeline
    parameters {
        string {
            name = "BRANCH_NAME"
            defaultValue = "main"
            description = "Branch to build"
        }
        choice {
            name = "ENVIRONMENT"
            choices = listOf("dev", "staging", "prod")
            description = "Target environment"
        }
        booleanParam {
            name = "SKIP_TESTS"
            defaultValue = false
            description = "Skip test execution"
        }
        password {
            name = "DEPLOY_KEY"
            description = "Deployment key"
        }
    }
    
    // Triggers automáticos
    triggers {
        cron("H 2 * * *")                      // Nightly build
        pollSCM("H/15 * * * *")                // Poll SCM cada 15 min
        upstream {                              // Trigger por upstream jobs
            upstreamProjects = "library-build"
            threshold = "SUCCESS"
        }
        githubPush()                           // GitHub webhook
    }
    
    // === STAGES DEFINITION ===
    
    stages {
        stage("Checkout") {
            steps {
                checkout(scm)
            }
        }
        
        stage("Build & Test") {
            // Stage con múltiples configuraciones
            parallel {
                stage("Build") {
                    agent {
                        docker {
                            image = "gradle:7.5-jdk17"
                        }
                    }
                    steps {
                        sh("./gradlew clean build")
                        archiveArtifacts {
                            artifacts = "**/build/libs/*.jar"
                            fingerprint = true
                        }
                    }
                }
                
                stage("Test") {
                    steps {
                        sh("./gradlew test integrationTest")
                        junit("**/build/test-results/**/*.xml")
                        publishTestResults {
                            testResultsPattern = "**/build/test-results/**/*.xml"
                            mergeResults = true
                        }
                    }
                }
                
                stage("Code Quality") {
                    steps {
                        sh("./gradlew sonarqube")
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
        }
        
        stage("Deploy") {
            // Stage condicional
            `when` {
                branch("main")
                not {
                    changeRequest()
                }
            }
            
            steps {
                script {
                    // Script block para lógica compleja
                    val version = readFile("VERSION.txt").trim()
                    env.set("RELEASE_VERSION", version)
                    
                    if (params.getString("ENVIRONMENT") == "prod") {
                        input {
                            message = "Deploy to production?"
                            ok = "Deploy"
                            submitterParameter = "APPROVED_BY"
                        }
                    }
                }
                
                sh("./deploy.sh \${ENVIRONMENT}")
            }
        }
    }
    
    // === POST ACTIONS ===
    
    post {
        always {
            // Siempre se ejecuta
            junit(allowEmptyResults = true, testResults = "**/*test-results.xml")
            publishTestResults {
                testResultsPattern = "**/target/surefire-reports/*.xml"
            }
        }
        
        success {
            // Solo en caso de éxito
            echo("Pipeline completed successfully! 🎉")
            slack {
                channel = "#deployments"
                color = "good" 
                message = "Deployment successful: ${env.getString("JOB_NAME")} - ${env.getString("BUILD_NUMBER")}"
            }
        }
        
        failure {
            // Solo en caso de fallo
            echo("Pipeline failed! 💥")
            emailext {
                to = "\$DEFAULT_RECIPIENTS"
                subject = "Build Failed: \${env.JOB_NAME} - \${env.BUILD_NUMBER}"
                body = "The build has failed. Please check the console output."
                attachLog = true
            }
        }
        
        unstable {
            // Build unstable (tests fallaron pero build OK)
            echo("Pipeline is unstable ⚠️")
        }
        
        aborted {
            // Pipeline cancelado
            echo("Pipeline was aborted 🛑")
        }
        
        changed {
            // Estado del build cambió desde la última ejecución
            echo("Pipeline status changed")
        }
        
        fixed {
            // Build se arregló después de estar roto
            echo("Pipeline is now fixed! ✅")
        }
        
        regression {
            // Build se rompió después de estar funcionando
            echo("Pipeline regression detected! ❌")
        }
        
        cleanup {
            // Limpieza final (siempre se ejecuta al final)
            cleanWs {
                cleanWhenAborted = true
                cleanWhenFailure = true
                cleanWhenNotBuilt = true
                cleanWhenSuccess = true
                cleanWhenUnstable = true
                deleteDirs = true
            }
        }
    }
}
```

### 2. **Steps Library**

#### Basic Steps
```kotlin
steps {
    // === SHELL & COMMANDS ===
    
    // Comando shell básico
    sh("echo 'Hello World'")
    
    // Shell con opciones avanzadas
    sh {
        script = """
            set -e
            echo "Building application..."
            ./gradlew clean build
        """.trimIndent()
        returnStdout = false
        returnStatus = false
        encoding = "UTF-8"
        label = "Build Application"
    }
    
    // Capturar output
    val version = sh {
        script = "cat VERSION.txt"
        returnStdout = true
    }.trim()
    
    // Capturar exit code
    val exitCode = sh {
        script = "test -f important-file.txt"
        returnStatus = true
    }
    
    // Batch/Windows
    bat("echo 'Windows command'")
    bat {
        script = "gradlew.bat build"
        returnStdout = true
    }
    
    // PowerShell
    powershell("Get-Date")
    powershell {
        script = "Test-Path 'C:\\important-file.txt'"
        returnStatus = true
    }
    
    // === OUTPUT & LOGGING ===
    
    echo("Simple message")
    echo("Build started at: ${new Date()}")
    
    // Logging con niveles
    log.info("Information message")
    log.warn("Warning message") 
    log.error("Error message")
    log.debug("Debug message")
    
    // === FILE OPERATIONS ===
    
    // Leer archivo
    val content = readFile("config.properties")
    val jsonContent = readJSON(file = "package.json")
    val yamlContent = readYaml(file = "config.yaml")
    
    // Escribir archivo
    writeFile(file = "output.txt", text = "Generated content")
    writeJSON(file = "result.json", json = mapOf("status" to "success"))
    writeYaml(file = "config.yaml", data = configMap)
    
    // Verificar existencia
    val fileExists = fileExists("important-file.txt")
    
    // === DIRECTORY OPERATIONS ===
    
    // Cambiar directorio
    dir("subproject") {
        sh("./gradlew build")
        archiveArtifacts("build/libs/*.jar")
    }
    
    // Crear directorio
    sh("mkdir -p build/reports")
    
    // === ENVIRONMENT ===
    
    // Variables de entorno temporales
    withEnv(listOf("PATH+EXTRA=/usr/local/bin", "JAVA_OPTS=-Xmx1g")) {
        sh("java -version")
        sh("which java")
    }
    
    // Credentials
    withCredentials(listOf(
        usernamePassword {
            credentialsId = "github-token"
            usernameVariable = "GITHUB_USER"
            passwordVariable = "GITHUB_TOKEN"
        },
        string {
            credentialsId = "api-key"
            variable = "API_KEY"
        },
        file {
            credentialsId = "keystore-file"
            variable = "KEYSTORE_PATH"
        }
    )) {
        sh("git clone https://\$GITHUB_USER:\$GITHUB_TOKEN@github.com/company/repo.git")
        sh("curl -H 'Authorization: Bearer \$API_KEY' \$API_URL")
    }
    
    // === CONTROL FLOW ===
    
    // Condicional
    script {
        if (env.getString("BRANCH_NAME") == "main") {
            echo("Main branch detected")
            sh("./deploy-production.sh")
        } else {
            echo("Feature branch")
            sh("./deploy-staging.sh")
        }
    }
    
    // Try-catch
    script {
        try {
            sh("risky-command")
        } catch (Exception e) {
            echo("Command failed: ${e.message}")
            currentBuild.result = "UNSTABLE"
        }
    }
    
    // === PARALLELISM ===
    
    // Ejecución paralela
    parallel(mapOf(
        "Unit Tests" to {
            sh("./gradlew test")
        },
        "Integration Tests" to {
            sh("./gradlew integrationTest") 
        },
        "Static Analysis" to {
            sh("./gradlew sonarqube")
        }
    ))
    
    // === RETRY & TIMEOUT ===
    
    // Reintentos
    retry(3) {
        sh("flaky-network-command")
    }
    
    // Timeout
    timeout(time = 10, unit = TimeUnit.MINUTES) {
        sh("long-running-command")
    }
    
    // Combinado
    retry(3) {
        timeout(time = 5, unit = TimeUnit.MINUTES) {
            sh("flaky-long-command")
        }
    }
    
    // === WAIT & SLEEP ===
    
    sleep(time = 30, unit = TimeUnit.SECONDS)
    waitUntil {
        script {
            val result = sh {
                script = "curl -f http://service/health"
                returnStatus = true
            }
            result == 0
        }
    }
}
```

#### Archive & Artifact Steps
```kotlin
steps {
    // === ARCHIVING ===
    
    // Archivar artefactos básico
    archiveArtifacts("**/build/libs/*.jar")
    
    // Archivar con opciones
    archiveArtifacts {
        artifacts = "build/libs/*.jar, build/reports/**"
        allowEmptyArchive = false
        fingerprint = true
        onlyIfSuccessful = false
        caseSensitive = true
        defaultExcludes = true
        excludes = "**/*-sources.jar"
    }
    
    // === TEST RESULTS ===
    
    // Publicar resultados JUnit
    junit("**/build/test-results/**/*.xml")
    
    // JUnit con opciones
    junit {
        testResults = "**/target/surefire-reports/*.xml"
        allowEmptyResults = true
        keepLongStdio = true
        healthScaleFactor = 1.0
        testDataPublishers = listOf()
    }
    
    // Publicar resultados personalizados
    publishTestResults {
        testResultsPattern = "**/test-results.xml"
        mergeResults = true
        failOnError = true
        allowEmptyResults = false
    }
    
    // === HTML REPORTS ===
    
    publishHTML {
        allowMissing = false
        alwaysLinkToLastBuild = true
        keepAll = true
        reportDir = "build/reports/jacoco/test/html"
        reportFiles = "index.html"
        reportName = "Coverage Report"
        reportTitles = "Code Coverage"
    }
    
    // === STASH/UNSTASH ===
    
    // Guardar archivos para uso posterior
    stash {
        name = "built-artifacts"
        includes = "build/libs/*.jar"
        excludes = "**/*-test.jar"
        allowEmpty = false
    }
    
    // Recuperar archivos guardados
    unstash("built-artifacts")
    
    // === FINGERPRINTING ===
    
    // Generar fingerprints
    fingerprint("**/*.jar")
    fingerprint {
        targets = "build/libs/*.jar, build/reports/**"
        caseSensitive = false
    }
}
```

#### SCM & Git Steps
```kotlin
steps {
    // === CHECKOUT ===
    
    // Checkout básico
    checkout(scm)
    
    // Git checkout específico
    checkout {
        git {
            url = "https://github.com/company/repository.git"
            branch = "main"
            credentialsId = "github-credentials"
            changelog = true
            poll = true
        }
    }
    
    // Checkout múltiple
    checkout {
        scm = listOf(
            git {
                url = "https://github.com/company/app.git"
                branch = env.getString("BRANCH_NAME")
                directory = "app"
            },
            git {
                url = "https://github.com/company/config.git" 
                branch = "main"
                directory = "config"
            }
        )
    }
    
    // === GIT OPERATIONS ===
    
    // Git commands
    sh("git status")
    sh("git log --oneline -10")
    
    // Git info helpers
    val gitCommit = env.getString("GIT_COMMIT")
    val gitBranch = env.getString("GIT_BRANCH")
    val gitUrl = env.getString("GIT_URL")
    
    // Git operations con script
    script {
        val changes = sh {
            script = "git diff --name-only HEAD~1"
            returnStdout = true
        }.trim().split('\n')
        
        if (changes.any { it.startsWith("src/") }) {
            echo("Source code changed, running full build")
            env.set("FULL_BUILD", "true")
        }
    }
    
    // === CHANGE DETECTION ===
    
    // Verificar cambios por path
    script {
        val docsChanged = sh {
            script = "git diff --name-only HEAD~1 | grep -q '^docs/'"
            returnStatus = true
        } == 0
        
        if (docsChanged) {
            echo("Documentation changed")
            sh("./build-docs.sh")
        }
    }
}
```

### 3. **Conditional Execution**

#### When Conditions
```kotlin
stage("Deploy to Production") {
    `when` {
        // === BRANCH CONDITIONS ===
        branch("main")                          // Branch específico
        branch("release/*")                     // Pattern matching
        not { branch("feature/*") }             // Negación
        anyOf {                                 // OR lógico
            branch("main")
            branch("develop")
        }
        allOf {                                 // AND lógico
            branch("main")
            environment {
                name = "DEPLOY_ENV"
                value = "production"
            }
        }
        
        // === ENVIRONMENT CONDITIONS ===
        environment {
            name = "ENVIRONMENT"
            value = "prod"
        }
        
        // === CHANGE CONDITIONS ===
        changeset("src/**")                     // Cambios en path
        changeset {
            pattern = "**/*.kt"
            caseSensitive = false
        }
        changeRequest()                         // Es Pull/Merge Request
        changeRequest {
            target = "main"
            branch = "feature/*"
        }
        
        // === BUILD CONDITIONS ===
        buildingTag()                           // Building un tag
        tag("v*")                              // Tag específico
        tag {
            pattern = "release-*"
            comparator = "REGEXP"
        }
        
        // === EXPRESSION CONDITIONS ===
        expression {                            // Expresión Groovy/Kotlin
            params.getString("DEPLOY") == "true"
        }
        
        // === TRIGGER CONDITIONS ===
        triggeredBy("TimerTrigger")            // Trigger específico
        triggeredBy("SCMTrigger")
        triggeredBy("UserIdCause")
        
        // === CUSTOM CONDITIONS ===
        beforeAgent = true                      // Evaluar antes de asignar agent
        
        // Condición compleja
        anyOf {
            allOf {
                branch("main")
                not { changeRequest() }
                environment {
                    name = "BUILD_TYPE" 
                    value = "release"
                }
            }
            allOf {
                tag("v*")
                expression {
                    params.getBoolean("FORCE_DEPLOY") == true
                }
            }
        }
    }
    
    steps {
        echo("Deploying to production...")
        sh("./deploy-production.sh")
    }
}
```

### 4. **Agent Configurations**

#### Agent Types
```kotlin
pipeline {
    // === AGENT GLOBAL ===
    
    agent {
        any()                                   // Cualquier agente disponible
        none()                                  // Sin agente (definir por stage)
        label("linux && docker")               // Por labels
        
        // Docker agent
        docker {
            image = "gradle:7.5-jdk17"
            args = "-v /var/run/docker.sock:/var/run/docker.sock"
            alwaysPull = true
            reuseNode = false
            registryUrl = "https://docker.company.com"
            registryCredentialsId = "docker-registry"
        }
        
        // Dockerfile agent
        dockerfile {
            filename = "Dockerfile.build"
            dir = "docker"
            label = "docker"
            additionalBuildArgs = "--build-arg VERSION=1.0"
            args = "-v /cache:/cache"
        }
        
        // Kubernetes agent
        kubernetes {
            yaml = """
                apiVersion: v1
                kind: Pod
                spec:
                  containers:
                  - name: gradle
                    image: gradle:7.5-jdk17
                    command: ['cat']
                    tty: true
                    resources:
                      requests:
                        memory: "1Gi"
                        cpu: "500m"
                      limits:
                        memory: "2Gi"
                        cpu: "1000m"
            """.trimIndent()
            
            // O desde archivo
            yamlFile = "k8s/build-pod.yaml"
            
            // Configuración adicional
            namespace = "jenkins-agents"
            serviceAccount = "jenkins"
            nodeSelector = mapOf("type" to "build-node")
        }
    }
    
    stages {
        stage("Build") {
            // === AGENT POR STAGE ===
            
            agent {
                docker {
                    image = "node:16-alpine"
                    reuseNode = true            // Reusar workspace del nodo principal
                }
            }
            
            steps {
                sh("npm install")
                sh("npm run build")
            }
        }
        
        stage("Multi-Agent Parallel") {
            parallel {
                stage("Linux Build") {
                    agent {
                        label("linux")
                    }
                    steps {
                        sh("./build-linux.sh")
                    }
                }
                
                stage("Windows Build") {
                    agent {
                        label("windows")
                    }
                    steps {
                        bat("build-windows.bat")
                    }
                }
                
                stage("macOS Build") {
                    agent {
                        label("macos")
                    }
                    steps {
                        sh("./build-macos.sh")
                    }
                }
            }
        }
    }
}
```

### 5. **Input & Approval Steps**

#### Interactive Steps
```kotlin
steps {
    // === INPUT BÁSICO ===
    
    input("Proceed with deployment?")
    
    // Input con opciones
    val userInput = input {
        message = "Choose deployment environment"
        ok = "Deploy"
        parameters = listOf(
            choice {
                name = "ENVIRONMENT"
                choices = listOf("staging", "production")
                description = "Target environment"
            },
            string {
                name = "VERSION"
                defaultValue = "latest"
                description = "Version to deploy"
            },
            booleanParam {
                name = "NOTIFY_TEAM"
                defaultValue = true
                description = "Send notification to team"
            }
        )
        submitter = "admin,devops"              // Usuarios autorizados
        submitterParameter = "APPROVED_BY"     // Variable con quien aprobó
    }
    
    // Usar el input
    echo("Deploying ${userInput.VERSION} to ${userInput.ENVIRONMENT}")
    if (userInput.NOTIFY_TEAM) {
        echo("Will notify team after deployment")
    }
    
    // === TIMEOUT EN INPUT ===
    
    timeout(time = 5, unit = TimeUnit.MINUTES) {
        val approval = input {
            message = "Deploy to production?"
            ok = "Yes, deploy"
            parameters = listOf(
                text {
                    name = "REASON"
                    description = "Reason for deployment"
                }
            )
        }
        
        echo("Deployment approved. Reason: ${approval.REASON}")
    }
    
    // === MILESTONE ===
    
    milestone(1)                               // Milestone para ordenar inputs
    input("Ready for integration tests?")
    milestone(2)
    input("Ready for production deployment?")
    milestone(3)
    
    // === CONDITIONAL INPUT ===
    
    script {
        if (env.getString("BRANCH_NAME") == "main") {
            input {
                message = "This will deploy to production. Continue?"
                ok = "Yes, I understand the risks"
                submitter = "senior-devs,devops-team"
            }
        }
    }
}
```

### 6. **Plugin Extensions**

#### Docker Plugin DSL
```kotlin
pipeline {
    agent { any() }
    
    stages {
        stage("Docker Build") {
            steps {
                // Plugin Docker syntax
                docker {
                    image = "myapp:${env.BUILD_NUMBER}"
                    
                    // Build image
                    build {
                        context = "."
                        dockerfile = "Dockerfile"
                        buildArgs = mapOf(
                            "VERSION" to env.getString("BUILD_NUMBER"),
                            "COMMIT" to env.getString("GIT_COMMIT")
                        )
                        target = "production"
                        pull = true
                        noCache = false
                        squash = true
                    }
                    
                    // Run container for testing
                    run {
                        args = "-p 8080:8080 -e ENV=test"
                        command = "npm test"
                    }
                    
                    // Push to registry
                    push {
                        registry = "docker.company.com"
                        repository = "myteam/myapp"
                        tags = listOf("latest", env.getString("BUILD_NUMBER"))
                        credentialsId = "docker-registry-creds"
                    }
                }
            }
        }
        
        stage("Docker Compose") {
            steps {
                dockerCompose {
                    file = "docker-compose.yml"
                    
                    up {
                        detach = true
                        build = true
                        forceRecreate = true
                    }
                    
                    exec {
                        service = "web"
                        command = "npm run test:integration"
                    }
                    
                    down {
                        removeVolumes = true
                        removeOrphans = true
                    }
                }
            }
        }
    }
}
```

#### Kubernetes Plugin DSL
```kotlin
stage("Deploy to K8s") {
    steps {
        kubernetes {
            cluster = "production"
            namespace = "myapp"
            credentialsId = "k8s-service-account"
            
            // Apply manifests
            apply {
                files = listOf("k8s/deployment.yaml", "k8s/service.yaml")
                recursive = true
                validate = true
                dryRun = false
            }
            
            // Wait for rollout
            rollout {
                resource = "deployment/myapp"
                action = "status"
                timeout = "300s"
            }
            
            // Update image
            setImage {
                resource = "deployment/myapp"
                container = "app"
                image = "myapp:${env.BUILD_NUMBER}"
            }
            
            // Port forward para tests
            portForward {
                resource = "service/myapp"
                ports = listOf("8080:80")
                background = true
            }
        }
    }
}
```

#### Slack Plugin DSL
```kotlin
post {
    success {
        slack {
            channel = "#deployments"
            color = "good"
            message = """
                ✅ Deployment successful!
                Job: ${env.JOB_NAME}
                Build: ${env.BUILD_NUMBER}
                Branch: ${env.GIT_BRANCH}
                Commit: ${env.GIT_COMMIT}
            """.trimIndent()
            
            // Attachments
            attachments = listOf(
                attachment {
                    title = "Build Details"
                    titleLink = env.getString("BUILD_URL")
                    color = "good"
                    fields = listOf(
                        field {
                            title = "Duration"
                            value = currentBuild.durationString
                            short = true
                        },
                        field {
                            title = "Tests"
                            value = "${currentBuild.testResultAction?.totalCount} tests passed"
                            short = true
                        }
                    )
                }
            )
            
            // Botones de acción
            actions = listOf(
                button {
                    text = "View Build"
                    url = env.getString("BUILD_URL")
                    style = "primary"
                },
                button {
                    text = "View Changes"
                    url = "${env.BUILD_URL}changes"
                }
            )
        }
    }
    
    failure {
        slack {
            channel = "#alerts"
            color = "danger"
            message = "❌ Build failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            
            // Mencionar usuarios
            mentions = listOf("@devops-team", "@${env.CHANGE_AUTHOR}")
        }
    }
}
```

## Validación y Type Safety

### Compile-Time Validation
```kotlin
// ✅ Válido - type-safe
pipeline {
    agent { any() }
    
    stages {
        stage("Build") {
            steps {
                sh("./gradlew build")  // String requerido
            }
        }
    }
}

// ❌ Error de compilación
pipeline {
    agent { any() }
    
    stages {
        stage("Build") {
            steps {
                sh()  // Error: missing required parameter 'script'
            }
        }
    }
}

// ❌ Error de compilación  
pipeline {
    stages {  // Error: 'agent' is required
        stage("Build") {
            steps {
                sh("./gradlew build")
            }
        }
    }
}
```

### Runtime Validation
```kotlin
// Validación de environment variables
pipeline {
    environment {
        // Validación automática
        set("REQUIRED_VAR", env("REQUIRED_VAR") 
            ?: error("REQUIRED_VAR must be set"))
    }
    
    stages {
        stage("Validate") {
            steps {
                script {
                    // Validación de parámetros
                    val deployEnv = params.getString("DEPLOY_ENV")
                    require(deployEnv in listOf("dev", "staging", "prod")) {
                        "Invalid DEPLOY_ENV: $deployEnv"
                    }
                    
                    // Validación de archivos
                    require(fileExists("Dockerfile")) {
                        "Dockerfile not found in repository root"
                    }
                    
                    // Validación de versión
                    val version = readFile("VERSION.txt").trim()
                    require(version.matches(Regex("""^\d+\.\d+\.\d+$"""))) {
                        "Invalid version format: $version"
                    }
                }
            }
        }
    }
}
```

---

Esta especificación DSL proporciona:
- **🎯 100% compatibilidad con Jenkins** manteniendo sintaxis familiar
- **⚡ Type-safety moderna** con validación compile-time
- **🔧 Extensibilidad completa** via plugins con DSL dinámico
- **🚀 Features modernas** con coroutines y Kotlin idioms
- **📚 Documentación viva** ejecutable como tests