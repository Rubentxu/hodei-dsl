# Ejemplos Avanzados - Hodei Pipeline DSL

## Pipelines Empresariales Complejos

### 1. **Microservices Deployment Pipeline**

Pipeline completo para deployment de microservicios con orquestación compleja:

```kotlin
// microservices-deployment.pipeline.kts
@file:DependsOn("org.yaml:snakeyaml:1.30")

import org.yaml.snakeyaml.Yaml

pipeline {
    agent { none() }
    
    parameters {
        choice {
            name = "ENVIRONMENT" 
            choices = listOf("development", "staging", "production")
            description = "Target deployment environment"
        }
        
        choice {
            name = "DEPLOYMENT_STRATEGY"
            choices = listOf("rolling", "blue-green", "canary")
            description = "Deployment strategy to use"
        }
        
        string {
            name = "SERVICES"
            defaultValue = "all"
            description = "Comma-separated services to deploy (or 'all')"
        }
        
        booleanParam {
            name = "RUN_SMOKE_TESTS"
            defaultValue = true
            description = "Run smoke tests after deployment"
        }
    }
    
    environment {
        set("DOCKER_REGISTRY", "docker.company.com")
        set("NAMESPACE", "microservices-${params.ENVIRONMENT}")
        set("HELM_REPO", "https://charts.company.com")
    }
    
    stages {
        stage("Preparation") {
            agent { 
                kubernetes {
                    yaml = """
                        apiVersion: v1
                        kind: Pod
                        spec:
                          containers:
                          - name: tools
                            image: alpine/k8s:1.24.0
                            command: ['cat']
                            tty: true
                    """
                }
            }
            steps {
                checkout(scm)
                
                script {
                    // Parse services configuration
                    val servicesYaml = readFile("services.yaml")
                    val yaml = Yaml()
                    val config = yaml.load<Map<String, Any>>(servicesYaml)
                    
                    val allServices = (config["services"] as List<Map<String, Any>>).map { it["name"] as String }
                    
                    val servicesToDeploy = if (params.getString("SERVICES") == "all") {
                        allServices
                    } else {
                        params.getString("SERVICES").split(",").map { it.trim() }
                    }
                    
                    env.set("SERVICES_TO_DEPLOY", servicesToDeploy.joinToString(","))
                    
                    echo("🎯 Services to deploy: ${servicesToDeploy.joinToString(", ")}")
                    echo("🌍 Target environment: ${params.ENVIRONMENT}")
                    echo("📦 Deployment strategy: ${params.DEPLOYMENT_STRATEGY}")
                    
                    // Validate services exist
                    val invalidServices = servicesToDeploy.filter { it !in allServices }
                    if (invalidServices.isNotEmpty()) {
                        error("Invalid services specified: ${invalidServices.joinToString(", ")}")
                    }
                }
            }
        }
        
        stage("Build & Test Services") {
            steps {
                script {
                    val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                    
                    // Create parallel stages for each service
                    val parallelStages = services.associate { serviceName ->
                        "Build $serviceName" to {
                            buildAndTestService(serviceName)
                        }
                    }
                    
                    parallel(parallelStages)
                }
            }
        }
        
        stage("Security & Compliance Scanning") {
            parallel {
                stage("Container Security Scan") {
                    agent {
                        docker {
                            image = "aquasec/trivy:latest"
                        }
                    }
                    steps {
                        script {
                            val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                            
                            services.forEach { serviceName ->
                                echo("🔒 Security scanning $serviceName...")
                                
                                sh("trivy image --format json --output ${serviceName}-security.json ${env.DOCKER_REGISTRY}/${serviceName}:${env.BUILD_NUMBER}")
                                
                                // Check for critical vulnerabilities
                                val criticalVulns = sh {
                                    script = "jq '.Results[]?.Vulnerabilities[]? | select(.Severity==\"CRITICAL\") | length' ${serviceName}-security.json | wc -l"
                                    returnStdout = true
                                }.trim().toInt()
                                
                                if (criticalVulns > 0) {
                                    echo("⚠️ Found $criticalVulns critical vulnerabilities in $serviceName")
                                    
                                    if (params.getString("ENVIRONMENT") == "production") {
                                        error("Critical vulnerabilities found in $serviceName - blocking production deployment")
                                    } else {
                                        echo("⚠️ Proceeding with deployment to non-production environment")
                                        currentBuild.result = "UNSTABLE"
                                    }
                                }
                            }
                        }
                        
                        archiveArtifacts {
                            artifacts = "*-security.json"
                            allowEmptyArchive = true
                        }
                    }
                }
                
                stage("Compliance Check") {
                    agent { any() }
                    steps {
                        echo("📋 Running compliance checks...")
                        
                        // Policy as Code validation
                        sh("opa test policies/")
                        
                        // License compliance
                        sh("license-scanner --config license-config.yaml .")
                        
                        // GDPR compliance check
                        sh("gdpr-scanner --config gdpr-config.yaml .")
                        
                        script {
                            if (params.getString("ENVIRONMENT") == "production") {
                                echo("🔍 Additional production compliance checks...")
                                
                                // SOX compliance
                                sh("sox-compliance-check --environment production")
                                
                                // Data retention policy check
                                sh("data-retention-policy-check .")
                            }
                        }
                    }
                }
                
                stage("Performance Baseline") {
                    agent {
                        docker {
                            image = "k6io/k6:latest"
                        }
                    }
                    steps {
                        echo("⚡ Establishing performance baseline...")
                        
                        script {
                            val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                            
                            services.forEach { serviceName ->
                                if (fileExists("performance-tests/${serviceName}.js")) {
                                    echo("📈 Running baseline performance test for $serviceName")
                                    
                                    sh("k6 run --out json=baseline-${serviceName}.json performance-tests/${serviceName}.js")
                                    
                                    // Store baseline for comparison
                                    archiveArtifacts {
                                        artifacts = "baseline-${serviceName}.json"
                                        fingerprint = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        stage("Infrastructure Preparation") {
            agent {
                docker {
                    image = "hashicorp/terraform:latest"
                }
            }
            steps {
                echo("🏗️ Preparing infrastructure...")
                
                dir("terraform/${params.ENVIRONMENT}") {
                    withCredentials(listOf(
                        string {
                            credentialsId = "aws-access-key"
                            variable = "AWS_ACCESS_KEY_ID"
                        },
                        string {
                            credentialsId = "aws-secret-key"
                            variable = "AWS_SECRET_ACCESS_KEY"
                        }
                    )) {
                        sh("terraform init")
                        sh("terraform plan -out=tfplan")
                        
                        if (params.getString("ENVIRONMENT") == "production") {
                            input {
                                message = "Apply Terraform changes to production?"
                                ok = "Apply"
                                submitter = "devops-team,platform-team"
                            }
                        }
                        
                        sh("terraform apply -auto-approve tfplan")
                        
                        // Extract important outputs
                        script {
                            val clusterName = sh {
                                script = "terraform output -raw cluster_name"
                                returnStdout = true
                            }.trim()
                            
                            val loadBalancerDns = sh {
                                script = "terraform output -raw load_balancer_dns"
                                returnStdout = true
                            }.trim()
                            
                            env.set("CLUSTER_NAME", clusterName)
                            env.set("LOAD_BALANCER_DNS", loadBalancerDns)
                            
                            echo("📋 Infrastructure ready:")
                            echo("  Cluster: $clusterName")
                            echo("  Load Balancer: $loadBalancerDns")
                        }
                    }
                }
            }
        }
        
        stage("Deploy Services") {
            steps {
                script {
                    val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                    val strategy = params.getString("DEPLOYMENT_STRATEGY")
                    
                    when (strategy) {
                        "rolling" -> deployWithRollingStrategy(services)
                        "blue-green" -> deployWithBlueGreenStrategy(services)
                        "canary" -> deployWithCanaryStrategy(services)
                    }
                }
            }
        }
        
        stage("Post-Deployment Verification") {
            parallel {
                stage("Health Checks") {
                    agent { any() }
                    steps {
                        echo("🏥 Running health checks...")
                        
                        script {
                            val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                            val loadBalancer = env.getString("LOAD_BALANCER_DNS")
                            
                            services.forEach { serviceName ->
                                echo("🔍 Health check for $serviceName...")
                                
                                timeout(time = 5, unit = TimeUnit.MINUTES) {
                                    waitUntil {
                                        script {
                                            val healthCheck = sh {
                                                script = "curl -f -s http://$loadBalancer/$serviceName/health"
                                                returnStatus = true
                                            }
                                            healthCheck == 0
                                        }
                                    }
                                }
                                
                                echo("✅ $serviceName is healthy")
                            }
                        }
                    }
                }
                
                stage("Smoke Tests") {
                    when {
                        expression { params.getBoolean("RUN_SMOKE_TESTS") }
                    }
                    agent {
                        docker {
                            image = "postman/newman:latest"
                        }
                    }
                    steps {
                        echo("💨 Running smoke tests...")
                        
                        script {
                            val loadBalancer = env.getString("LOAD_BALANCER_DNS")
                            
                            sh("""
                                newman run smoke-tests/microservices-smoke-tests.json \
                                  --environment smoke-tests/${params.ENVIRONMENT}.json \
                                  --global-var "base_url=http://$loadBalancer" \
                                  --reporters cli,json \
                                  --reporter-json-export smoke-test-results.json
                            """)
                        }
                        
                        archiveArtifacts {
                            artifacts = "smoke-test-results.json"
                            allowEmptyArchive = false
                        }
                    }
                }
                
                stage("Performance Regression Test") {
                    agent {
                        docker {
                            image = "k6io/k6:latest"
                        }
                    }
                    steps {
                        echo("📊 Running performance regression tests...")
                        
                        script {
                            val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                            val loadBalancer = env.getString("LOAD_BALANCER_DNS")
                            
                            services.forEach { serviceName ->
                                if (fileExists("performance-tests/${serviceName}.js")) {
                                    echo("⚡ Performance test for $serviceName")
                                    
                                    sh("""
                                        k6 run \
                                          --env BASE_URL=http://$loadBalancer \
                                          --out json=performance-${serviceName}.json \
                                          performance-tests/${serviceName}.js
                                    """)
                                    
                                    // Compare with baseline
                                    script {
                                        if (fileExists("baseline-${serviceName}.json")) {
                                            echo("📈 Comparing performance with baseline...")
                                            
                                            val comparison = sh {
                                                script = "performance-compare baseline-${serviceName}.json performance-${serviceName}.json"
                                                returnStdout = true
                                            }.trim()
                                            
                                            echo("Performance comparison result: $comparison")
                                            
                                            // Check if performance degraded significantly
                                            val degradation = sh {
                                                script = "echo '$comparison' | jq -r '.degradation_percentage'"
                                                returnStdout = true
                                            }.trim().toDouble()
                                            
                                            if (degradation > 20.0) {
                                                echo("⚠️ Performance degraded by ${degradation}% for $serviceName")
                                                
                                                if (params.getString("ENVIRONMENT") == "production") {
                                                    input {
                                                        message = "Performance degraded by ${degradation}%. Continue deployment?"
                                                        ok = "Continue"
                                                        submitter = "performance-team,devops-team"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        stage("Monitoring & Alerting Setup") {
            agent { any() }
            steps {
                echo("📊 Setting up monitoring and alerting...")
                
                script {
                    val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                    val environment = params.getString("ENVIRONMENT")
                    
                    // Deploy Prometheus rules
                    services.forEach { serviceName ->
                        if (fileExists("monitoring/prometheus-rules-${serviceName}.yaml")) {
                            sh("""
                                kubectl apply -f monitoring/prometheus-rules-${serviceName}.yaml \
                                  --namespace monitoring
                            """)
                        }
                    }
                    
                    // Deploy Grafana dashboards
                    services.forEach { serviceName ->
                        if (fileExists("monitoring/grafana-dashboard-${serviceName}.json")) {
                            sh("""
                                grafana-dashboard-deploy \
                                  --dashboard monitoring/grafana-dashboard-${serviceName}.json \
                                  --environment $environment
                            """)
                        }
                    }
                    
                    // Setup alerting rules
                    sh("""
                        alertmanager-config-deploy \
                          --environment $environment \
                          --config monitoring/alertmanager-${environment}.yaml
                    """)
                    
                    echo("✅ Monitoring and alerting configured")
                }
            }
        }
        
        stage("Documentation Update") {
            agent {
                docker {
                    image = "node:18-alpine"
                }
            }
            steps {
                echo("📚 Updating deployment documentation...")
                
                script {
                    val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                    val environment = params.getString("ENVIRONMENT")
                    val buildNumber = env.getString("BUILD_NUMBER")
                    val timestamp = new Date().toString()
                    
                    // Generate deployment report
                    val deploymentReport = """
                        # Deployment Report
                        
                        **Environment**: $environment
                        **Build Number**: $buildNumber
                        **Deployment Time**: $timestamp
                        **Services Deployed**: ${services.joinToString(", ")}
                        **Deployment Strategy**: ${params.DEPLOYMENT_STRATEGY}
                        
                        ## Service Versions
                        ${services.joinToString("\n") { "- $it: ${buildNumber}" }}
                        
                        ## Infrastructure
                        - Cluster: ${env.CLUSTER_NAME}
                        - Load Balancer: ${env.LOAD_BALANCER_DNS}
                        
                        ## Quality Gates
                        - Security Scan: ✅ Passed
                        - Compliance Check: ✅ Passed
                        - Performance Test: ✅ Passed
                        - Smoke Tests: ✅ Passed
                    """.trimIndent()
                    
                    writeFile(file = "deployment-report.md", text = deploymentReport)
                    
                    // Update service registry
                    services.forEach { serviceName ->
                        sh("""
                            service-registry-update \
                              --service $serviceName \
                              --version $buildNumber \
                              --environment $environment \
                              --endpoint http://${env.LOAD_BALANCER_DNS}/$serviceName
                        """)
                    }
                    
                    // Update API documentation
                    sh("api-docs-generator --services ${services.joinToString(",")} --output api-docs/")
                    
                    archiveArtifacts {
                        artifacts = "deployment-report.md, api-docs/**"
                        fingerprint = true
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo("📊 Collecting deployment metrics...")
            
            script {
                val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                val environment = params.getString("ENVIRONMENT")
                
                // Send metrics to monitoring system
                sh("""
                    deployment-metrics-collector \
                      --environment $environment \
                      --services ${services.joinToString(",")} \
                      --build-number ${env.BUILD_NUMBER} \
                      --duration ${currentBuild.duration}
                """)
            }
        }
        
        success {
            echo("🎉 Microservices deployment completed successfully!")
            
            script {
                val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                val environment = params.getString("ENVIRONMENT")
                val loadBalancer = env.getString("LOAD_BALANCER_DNS")
                
                // Send success notification
                slack {
                    channel = "#deployments"
                    color = "good"
                    message = """
                        ✅ **Deployment Successful**
                        
                        **Environment**: $environment
                        **Services**: ${services.joinToString(", ")}
                        **Strategy**: ${params.DEPLOYMENT_STRATEGY}
                        **Endpoint**: http://$loadBalancer
                        **Build**: ${env.BUILD_NUMBER}
                        
                        All quality gates passed! 🚀
                    """.trimIndent()
                }
                
                // Update deployment dashboard
                sh("""
                    deployment-dashboard-update \
                      --environment $environment \
                      --status success \
                      --services ${services.joinToString(",")} \
                      --endpoint http://$loadBalancer
                """)
            }
        }
        
        failure {
            echo("💥 Microservices deployment failed!")
            
            script {
                val services = env.getString("SERVICES_TO_DEPLOY").split(",")
                val environment = params.getString("ENVIRONMENT")
                
                // Rollback if this was a production deployment
                if (environment == "production") {
                    echo("🔄 Initiating automatic rollback...")
                    
                    services.forEach { serviceName ->
                        sh("""
                            helm rollback $serviceName-$environment \
                              --namespace ${env.NAMESPACE} \
                              --wait
                        """)
                    }
                    
                    echo("✅ Rollback completed")
                }
                
                // Send failure notification with detailed logs
                slack {
                    channel = "#alerts"
                    color = "danger"
                    message = """
                        ❌ **Deployment Failed**
                        
                        **Environment**: $environment
                        **Services**: ${services.joinToString(", ")}
                        **Build**: ${env.BUILD_NUMBER}
                        **Error**: ${currentBuild.description ?: "Unknown error"}
                        
                        ${if (environment == "production") "🔄 Automatic rollback initiated" else ""}
                        
                        Check logs: ${env.BUILD_URL}console
                    """.trimIndent()
                }
            }
        }
        
        unstable {
            echo("⚠️ Deployment completed with warnings")
            
            slack {
                channel = "#deployments"
                color = "warning"
                message = """
                    ⚠️ **Deployment Unstable**
                    
                    **Environment**: ${params.ENVIRONMENT}
                    **Build**: ${env.BUILD_NUMBER}
                    
                    Deployment succeeded but some quality gates failed.
                    Please review: ${env.BUILD_URL}
                """.trimIndent()
            }
        }
    }
}

// Helper functions for deployment strategies
fun buildAndTestService(serviceName: String) {
    echo("🔨 Building and testing $serviceName...")
    
    dir("services/$serviceName") {
        // Build the service
        sh("./gradlew clean build")
        
        // Run service-specific tests
        sh("./gradlew test integrationTest")
        
        // Build and push Docker image
        script {
            val imageTag = "${env.DOCKER_REGISTRY}/$serviceName:${env.BUILD_NUMBER}"
            
            sh("docker build -t $imageTag .")
            sh("docker push $imageTag")
            
            echo("✅ $serviceName built and pushed: $imageTag")
        }
    }
}

fun deployWithRollingStrategy(services: List<String>) {
    echo("🔄 Deploying with rolling update strategy...")
    
    services.forEach { serviceName ->
        sh("""
            helm upgrade --install $serviceName-${params.ENVIRONMENT} \
              charts/$serviceName \
              --set image.tag=${env.BUILD_NUMBER} \
              --set environment=${params.ENVIRONMENT} \
              --namespace ${env.NAMESPACE} \
              --wait \
              --timeout=600s
        """)
        
        echo("✅ $serviceName deployed successfully")
    }
}

fun deployWithBlueGreenStrategy(services: List<String>) {
    echo("🔵🟢 Deploying with blue-green strategy...")
    
    services.forEach { serviceName ->
        // Deploy to green environment
        sh("""
            helm upgrade --install $serviceName-${params.ENVIRONMENT}-green \
              charts/$serviceName \
              --set image.tag=${env.BUILD_NUMBER} \
              --set environment=${params.ENVIRONMENT}-green \
              --namespace ${env.NAMESPACE}-green \
              --wait \
              --timeout=600s
        """)
        
        // Verify green deployment
        sh("kubectl get pods -n ${env.NAMESPACE}-green -l app=$serviceName")
        
        // Switch traffic to green
        sh("""
            kubectl patch service $serviceName-service \
              -n ${env.NAMESPACE} \
              -p '{"spec":{"selector":{"version":"green"}}}'
        """)
        
        echo("✅ $serviceName blue-green deployment completed")
    }
}

fun deployWithCanaryStrategy(services: List<String>) {
    echo("🐤 Deploying with canary strategy...")
    
    services.forEach { serviceName ->
        // Deploy canary version (10% traffic)
        sh("""
            helm upgrade --install $serviceName-${params.ENVIRONMENT}-canary \
              charts/$serviceName \
              --set image.tag=${env.BUILD_NUMBER} \
              --set environment=${params.ENVIRONMENT} \
              --set canary.enabled=true \
              --set canary.weight=10 \
              --namespace ${env.NAMESPACE} \
              --wait \
              --timeout=600s
        """)
        
        // Monitor canary metrics for 10 minutes
        echo("📊 Monitoring canary deployment for $serviceName...")
        sleep(time = 10, unit = TimeUnit.MINUTES)
        
        // Check canary health metrics
        val canaryHealth = sh {
            script = "canary-health-check --service $serviceName --duration 10m"
            returnStatus = true
        }
        
        if (canaryHealth == 0) {
            // Promote canary to 100%
            sh("""
                helm upgrade $serviceName-${params.ENVIRONMENT} \
                  charts/$serviceName \
                  --set image.tag=${env.BUILD_NUMBER} \
                  --set environment=${params.ENVIRONMENT} \
                  --set canary.enabled=false \
                  --namespace ${env.NAMESPACE} \
                  --wait
            """)
            
            echo("✅ $serviceName canary promoted to full deployment")
        } else {
            // Rollback canary
            sh("helm delete $serviceName-${params.ENVIRONMENT}-canary --namespace ${env.NAMESPACE}")
            error("❌ $serviceName canary deployment failed health checks")
        }
    }
}
```

### 2. **Multi-Cloud Infrastructure Pipeline**

Pipeline para gestión de infraestructura en múltiples clouds:

```kotlin
// multi-cloud-infrastructure.pipeline.kts
@file:DependsOn("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
@file:DependsOn("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.2")

import com.fasterxml.jackson.module.kotlin.*
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper

pipeline {
    agent { none() }
    
    parameters {
        choice {
            name = "CLOUDS"
            choices = listOf("aws", "azure", "gcp", "aws,azure", "aws,gcp", "azure,gcp", "all")
            description = "Target cloud providers"
        }
        
        choice {
            name = "ACTION"
            choices = listOf("plan", "apply", "destroy", "drift-detection")
            description = "Terraform action to perform"
        }
        
        choice {
            name = "ENVIRONMENT"
            choices = listOf("dev", "staging", "prod")
            description = "Target environment"
        }
        
        booleanParam {
            name = "AUTO_APPROVE"
            defaultValue = false
            description = "Auto-approve Terraform changes (use with caution)"
        }
    }
    
    environment {
        set("TF_VAR_environment", params.getString("ENVIRONMENT"))
        set("TF_VAR_build_number", env("BUILD_NUMBER"))
        set("TF_IN_AUTOMATION", "true")
    }
    
    stages {
        stage("Configuration Validation") {
            agent {
                docker {
                    image = "hashicorp/terraform:latest"
                }
            }
            steps {
                checkout(scm)
                
                echo("🔍 Validating infrastructure configuration...")
                
                script {
                    val clouds = if (params.getString("CLOUDS") == "all") {
                        listOf("aws", "azure", "gcp")
                    } else {
                        params.getString("CLOUDS").split(",").map { it.trim() }
                    }
                    
                    env.set("TARGET_CLOUDS", clouds.joinToString(","))
                    
                    echo("🎯 Target clouds: ${clouds.joinToString(", ")}")
                    echo("🚀 Action: ${params.ACTION}")
                    echo("🌍 Environment: ${params.ENVIRONMENT}")
                    
                    // Validate Terraform configuration for each cloud
                    clouds.forEach { cloud ->
                        dir("terraform/$cloud") {
                            sh("terraform init -backend=false")
                            sh("terraform validate")
                            echo("✅ $cloud configuration is valid")
                        }
                    }
                    
                    // Validate configuration consistency
                    validateMultiCloudConsistency(clouds)
                }
            }
        }
        
        stage("Security & Compliance Scanning") {
            parallel {
                stage("Terraform Security Scan") {
                    agent {
                        docker {
                            image = "bridgecrew/checkov:latest"
                        }
                    }
                    steps {
                        script {
                            val clouds = env.getString("TARGET_CLOUDS").split(",")
                            
                            clouds.forEach { cloud ->
                                echo("🔒 Security scanning $cloud infrastructure...")
                                
                                sh("""
                                    checkov -d terraform/$cloud \
                                      --framework terraform \
                                      --output json \
                                      --output-file-path $cloud-security-scan.json \
                                      --soft-fail
                                """)
                                
                                // Check for critical security issues
                                val criticalIssues = sh {
                                    script = "jq '.results.failed_checks | map(select(.severity == \"HIGH\")) | length' $cloud-security-scan.json"
                                    returnStdout = true
                                }.trim().toInt()
                                
                                if (criticalIssues > 0) {
                                    echo("⚠️ Found $criticalIssues high-severity security issues in $cloud")
                                    
                                    if (params.getString("ENVIRONMENT") == "prod") {
                                        currentBuild.result = "UNSTABLE"
                                    }
                                }
                            }
                        }
                        
                        archiveArtifacts {
                            artifacts = "*-security-scan.json"
                            allowEmptyArchive = true
                        }
                    }
                }
                
                stage("Cost Estimation") {
                    agent {
                        docker {
                            image = "infracost/infracost:latest"
                        }
                    }
                    steps {
                        withCredentials(listOf(
                            string {
                                credentialsId = "infracost-api-key"
                                variable = "INFRACOST_API_KEY"
                            }
                        )) {
                            script {
                                val clouds = env.getString("TARGET_CLOUDS").split(",")
                                var totalMonthlyCost = 0.0
                                
                                clouds.forEach { cloud ->
                                    echo("💰 Cost estimation for $cloud...")
                                    
                                    dir("terraform/$cloud") {
                                        sh("terraform init")
                                        sh("terraform plan -out=tfplan")
                                        sh("infracost breakdown --path=tfplan --format=json --out-file=${cloud}-cost.json")
                                        
                                        val costJson = readFile("${cloud}-cost.json")
                                        val mapper = ObjectMapper()
                                        val costData = mapper.readTree(costJson)
                                        val monthlyCost = costData.get("totalMonthlyCost")?.asDouble() ?: 0.0
                                        
                                        totalMonthlyCost += monthlyCost
                                        
                                        echo("💸 $cloud estimated monthly cost: \$${String.format("%.2f", monthlyCost)}")
                                    }
                                }
                                
                                echo("💸 Total estimated monthly cost: \$${String.format("%.2f", totalMonthlyCost)}")
                                env.set("TOTAL_MONTHLY_COST", totalMonthlyCost.toString())
                                
                                // Cost gate for production
                                if (params.getString("ENVIRONMENT") == "prod" && totalMonthlyCost > 10000.0) {
                                    input {
                                        message = "High cost detected (\$${String.format("%.2f", totalMonthlyCost)}/month). Continue?"
                                        ok = "Approve"
                                        submitter = "finance-team,platform-team"
                                    }
                                }
                            }
                        }
                        
                        archiveArtifacts {
                            artifacts = "terraform/**/tfplan, terraform/**/*-cost.json"
                            allowEmptyArchive = true
                        }
                    }
                }
                
                stage("Compliance Check") {
                    agent { any() }
                    steps {
                        echo("📋 Running compliance checks...")
                        
                        script {
                            val clouds = env.getString("TARGET_CLOUDS").split(",")
                            val environment = params.getString("ENVIRONMENT")
                            
                            clouds.forEach { cloud ->
                                echo("🔍 Compliance check for $cloud...")
                                
                                // Check cloud-specific compliance
                                when (cloud) {
                                    "aws" -> {
                                        sh("aws-config-compliance-check --environment $environment")
                                        sh("aws-security-hub-compliance-check")
                                    }
                                    "azure" -> {
                                        sh("azure-policy-compliance-check --environment $environment")
                                        sh("azure-security-center-compliance-check")
                                    }
                                    "gcp" -> {
                                        sh("gcp-security-command-center-check")
                                        sh("gcp-policy-compliance-check --environment $environment")
                                    }
                                }
                            }
                            
                            // Cross-cloud compliance
                            if (clouds.size > 1) {
                                echo("🌐 Multi-cloud compliance check...")
                                sh("multi-cloud-compliance-check --clouds ${clouds.joinToString(",")}")
                            }
                        }
                    }
                }
            }
        }
        
        stage("Infrastructure Planning") {
            steps {
                script {
                    val clouds = env.getString("TARGET_CLOUDS").split(",")
                    val action = params.getString("ACTION")
                    
                    if (action in listOf("plan", "apply")) {
                        // Create parallel stages for each cloud
                        val planStages = clouds.associate { cloud ->
                            "Plan $cloud" to {
                                planInfrastructure(cloud)
                            }
                        }
                        
                        parallel(planStages)
                    } else if (action == "drift-detection") {
                        // Create parallel drift detection stages
                        val driftStages = clouds.associate { cloud ->
                            "Drift Detection $cloud" to {
                                detectDrift(cloud)
                            }
                        }
                        
                        parallel(driftStages)
                    }
                }
            }
        }
        
        stage("Cross-Cloud Dependencies") {
            when {
                expression {
                    val clouds = env.getString("TARGET_CLOUDS").split(",")
                    clouds.size > 1 && params.getString("ACTION") in listOf("plan", "apply")
                }
            }
            agent { any() }
            steps {
                echo("🔗 Analyzing cross-cloud dependencies...")
                
                script {
                    val clouds = env.getString("TARGET_CLOUDS").split(",")
                    
                    // Generate dependency graph
                    val dependencyGraph = analyzeCrossCloudDependencies(clouds)
                    
                    echo("📊 Cross-cloud dependency analysis:")
                    dependencyGraph.forEach { (source, targets) ->
                        echo("  $source → ${targets.joinToString(", ")}")
                    }
                    
                    // Determine deployment order
                    val deploymentOrder = resolveDependencyOrder(dependencyGraph)
                    env.set("DEPLOYMENT_ORDER", deploymentOrder.joinToString(","))
                    
                    echo("📋 Deployment order: ${deploymentOrder.joinToString(" → ")}")
                }
            }
        }
        
        stage("Infrastructure Deployment") {
            when {
                expression { params.getString("ACTION") == "apply" }
            }
            steps {
                script {
                    val deploymentOrder = env.getString("DEPLOYMENT_ORDER").split(",")
                    val autoApprove = params.getBoolean("AUTO_APPROVE")
                    val environment = params.getString("ENVIRONMENT")
                    
                    if (!autoApprove && environment == "prod") {
                        input {
                            message = "Apply infrastructure changes to production?"
                            ok = "Deploy"
                            submitter = "devops-team,platform-team"
                            parameters = listOf(
                                text {
                                    name = "CHANGE_REASON"
                                    description = "Reason for this infrastructure change"
                                }
                            )
                        }
                    }
                    
                    // Deploy in dependency order
                    deploymentOrder.forEach { cloud ->
                        echo("🚀 Deploying $cloud infrastructure...")
                        deployInfrastructure(cloud, autoApprove)
                    }
                }
            }
        }
        
        stage("Infrastructure Testing") {
            when {
                expression { params.getString("ACTION") == "apply" }
            }
            parallel {
                stage("Connectivity Tests") {
                    agent { any() }
                    steps {
                        script {
                            val clouds = env.getString("TARGET_CLOUDS").split(",")
                            
                            echo("🔌 Testing infrastructure connectivity...")
                            
                            clouds.forEach { cloud ->
                                echo("Testing $cloud connectivity...")
                                
                                // Test basic connectivity
                                sh("infrastructure-connectivity-test --cloud $cloud --environment ${params.ENVIRONMENT}")
                                
                                // Test cross-cloud connectivity if multiple clouds
                                if (clouds.size > 1) {
                                    val otherClouds = clouds.filter { it != cloud }
                                    otherClouds.forEach { otherCloud ->
                                        sh("cross-cloud-connectivity-test --from $cloud --to $otherCloud")
                                    }
                                }
                            }
                        }
                    }
                }
                
                stage("Performance Tests") {
                    agent {
                        docker {
                            image = "loadimpact/k6:latest"
                        }
                    }
                    steps {
                        script {
                            val clouds = env.getString("TARGET_CLOUDS").split(",")
                            
                            echo("⚡ Running infrastructure performance tests...")
                            
                            clouds.forEach { cloud ->
                                if (fileExists("performance-tests/infrastructure-${cloud}.js")) {
                                    sh("""
                                        k6 run \
                                          --env CLOUD=$cloud \
                                          --env ENVIRONMENT=${params.ENVIRONMENT} \
                                          --out json=performance-${cloud}.json \
                                          performance-tests/infrastructure-${cloud}.js
                                    """)
                                }
                            }
                        }
                        
                        archiveArtifacts {
                            artifacts = "performance-*.json"
                            allowEmptyArchive = true
                        }
                    }
                }
                
                stage("Security Tests") {
                    agent { any() }
                    steps {
                        script {
                            val clouds = env.getString("TARGET_CLOUDS").split(",")
                            
                            echo("🔒 Running infrastructure security tests...")
                            
                            clouds.forEach { cloud ->
                                // Network security tests
                                sh("network-security-test --cloud $cloud --environment ${params.ENVIRONMENT}")
                                
                                // Access control tests
                                sh("access-control-test --cloud $cloud --environment ${params.ENVIRONMENT}")
                                
                                // Encryption tests
                                sh("encryption-test --cloud $cloud --environment ${params.ENVIRONMENT}")
                            }
                        }
                    }
                }
            }
        }
        
        stage("Monitoring & Alerting Setup") {
            when {
                expression { params.getString("ACTION") == "apply" }
            }
            agent { any() }
            steps {
                echo("📊 Setting up monitoring and alerting...")
                
                script {
                    val clouds = env.getString("TARGET_CLOUDS").split(",")
                    val environment = params.getString("ENVIRONMENT")
                    
                    clouds.forEach { cloud ->
                        echo("📈 Setting up monitoring for $cloud...")
                        
                        // Deploy monitoring stack
                        sh("""
                            monitoring-stack-deploy \
                              --cloud $cloud \
                              --environment $environment \
                              --config monitoring/configs/$cloud-$environment.yaml
                        """)
                        
                        // Setup alerting rules
                        sh("""
                            alerting-rules-deploy \
                              --cloud $cloud \
                              --environment $environment \
                              --rules monitoring/alerts/$cloud-alerts.yaml
                        """)
                    }
                    
                    // Setup cross-cloud monitoring
                    if (clouds.size > 1) {
                        sh("""
                            cross-cloud-monitoring-setup \
                              --clouds ${clouds.joinToString(",")} \
                              --environment $environment
                        """)
                    }
                }
            }
        }
        
        stage("Documentation & State Management") {
            when {
                expression { params.getString("ACTION") == "apply" }
            }
            agent { any() }
            steps {
                echo("📚 Updating documentation and state management...")
                
                script {
                    val clouds = env.getString("TARGET_CLOUDS").split(",")
                    val environment = params.getString("ENVIRONMENT")
                    val buildNumber = env.getString("BUILD_NUMBER")
                    
                    // Generate infrastructure documentation
                    sh("""
                        infrastructure-docs-generator \
                          --clouds ${clouds.joinToString(",")} \
                          --environment $environment \
                          --output docs/infrastructure/
                    """)
                    
                    // Update infrastructure inventory
                    clouds.forEach { cloud ->
                        sh("""
                            infrastructure-inventory-update \
                              --cloud $cloud \
                              --environment $environment \
                              --build $buildNumber
                        """)
                    }
                    
                    // Update disaster recovery documentation
                    sh("""
                        disaster-recovery-docs-update \
                          --clouds ${clouds.joinToString(",")} \
                          --environment $environment
                    """)
                    
                    archiveArtifacts {
                        artifacts = "docs/infrastructure/**"
                        fingerprint = true
                    }
                }
            }
        }
    }
    
    post {
        always {
            script {
                val clouds = env.getString("TARGET_CLOUDS").split(",")
                val action = params.getString("ACTION")
                val environment = params.getString("ENVIRONMENT")
                
                echo("📊 Collecting infrastructure metrics...")
                
                // Collect Terraform state metrics
                clouds.forEach { cloud ->
                    sh("""
                        terraform-state-metrics \
                          --cloud $cloud \
                          --environment $environment \
                          --action $action
                    """)
                }
                
                // Collect cost metrics
                if (env.contains("TOTAL_MONTHLY_COST")) {
                    sh("""
                        cost-metrics-update \
                          --environment $environment \
                          --monthly-cost ${env.TOTAL_MONTHLY_COST} \
                          --clouds ${clouds.joinToString(",")}
                    """)
                }
            }
        }
        
        success {
            script {
                val clouds = env.getString("TARGET_CLOUDS").split(",")
                val action = params.getString("ACTION")
                val environment = params.getString("ENVIRONMENT")
                
                echo("🎉 Infrastructure ${action} completed successfully!")
                
                slack {
                    channel = "#infrastructure"
                    color = "good"
                    message = """
                        ✅ **Infrastructure $action Successful**
                        
                        **Environment**: $environment
                        **Clouds**: ${clouds.joinToString(", ")}
                        **Build**: ${env.BUILD_NUMBER}
                        ${if (env.contains("TOTAL_MONTHLY_COST")) "**Monthly Cost**: \$${env.TOTAL_MONTHLY_COST}" else ""}
                        
                        All infrastructure changes applied successfully! 🏗️
                    """.trimIndent()
                }
            }
        }
        
        failure {
            script {
                val action = params.getString("ACTION")
                val environment = params.getString("ENVIRONMENT")
                
                echo("💥 Infrastructure ${action} failed!")
                
                // Attempt automatic rollback for production
                if (environment == "prod" && action == "apply") {
                    echo("🔄 Attempting automatic rollback...")
                    
                    val clouds = env.getString("TARGET_CLOUDS").split(",")
                    
                    clouds.forEach { cloud ->
                        try {
                            sh("""
                                terraform-rollback \
                                  --cloud $cloud \
                                  --environment $environment \
                                  --to-previous-state
                            """)
                            echo("✅ Rollback successful for $cloud")
                        } catch (Exception e) {
                            echo("❌ Rollback failed for $cloud: ${e.message}")
                        }
                    }
                }
                
                slack {
                    channel = "#infrastructure-alerts"
                    color = "danger"
                    message = """
                        ❌ **Infrastructure $action Failed**
                        
                        **Environment**: $environment
                        **Build**: ${env.BUILD_NUMBER}
                        **Error**: Infrastructure deployment failed
                        
                        ${if (environment == "prod") "🔄 Automatic rollback attempted" else ""}
                        
                        Check logs: ${env.BUILD_URL}console
                    """.trimIndent()
                }
            }
        }
    }
}

// Helper functions
fun validateMultiCloudConsistency(clouds: List<String>) {
    echo("🔍 Validating multi-cloud configuration consistency...")
    
    // Check naming conventions
    sh("multi-cloud-naming-validator --clouds ${clouds.joinToString(",")}")
    
    // Check resource tagging consistency
    sh("resource-tagging-validator --clouds ${clouds.joinToString(",")}")
    
    // Check security group consistency
    sh("security-group-consistency-check --clouds ${clouds.joinToString(",")}")
}

fun planInfrastructure(cloud: String): Unit = runBlocking {
    echo("📋 Planning infrastructure for $cloud...")
    
    dir("terraform/$cloud") {
        // Initialize Terraform
        sh("terraform init")
        
        // Create plan
        sh("terraform plan -out=tfplan-$cloud -detailed-exitcode") {
            returnStatus = true
        }.let { exitCode ->
            when (exitCode) {
                0 -> echo("✅ No changes needed for $cloud")
                2 -> echo("📝 Changes planned for $cloud")
                else -> error("❌ Terraform plan failed for $cloud")
            }
        }
        
        // Generate human-readable plan
        sh("terraform show -no-color tfplan-$cloud > tfplan-$cloud.txt")
        
        archiveArtifacts {
            artifacts = "tfplan-$cloud, tfplan-$cloud.txt"
            fingerprint = true
        }
    }
}

fun detectDrift(cloud: String) {
    echo("🔍 Detecting configuration drift for $cloud...")
    
    dir("terraform/$cloud") {
        sh("terraform init")
        
        // Refresh state and detect drift
        val driftExitCode = sh {
            script = "terraform plan -detailed-exitcode -refresh-only"
            returnStatus = true
        }
        
        when (driftExitCode) {
            0 -> echo("✅ No drift detected in $cloud")
            2 -> {
                echo("⚠️ Configuration drift detected in $cloud")
                currentBuild.result = "UNSTABLE"
                
                // Generate drift report
                sh("terraform show -no-color > drift-report-$cloud.txt")
                
                archiveArtifacts {
                    artifacts = "drift-report-$cloud.txt"
                    fingerprint = true
                }
            }
            else -> error("❌ Drift detection failed for $cloud")
        }
    }
}

fun deployInfrastructure(cloud: String, autoApprove: Boolean) {
    echo("🚀 Deploying infrastructure for $cloud...")
    
    dir("terraform/$cloud") {
        val approveFlag = if (autoApprove) "-auto-approve" else ""
        
        sh("terraform apply $approveFlag tfplan-$cloud")
        
        // Generate outputs
        sh("terraform output -json > outputs-$cloud.json")
        
        archiveArtifacts {
            artifacts = "outputs-$cloud.json"
            fingerprint = true
        }
        
        echo("✅ $cloud infrastructure deployed successfully")
    }
}

fun analyzeCrossCloudDependencies(clouds: List<String>): Map<String, List<String>> {
    // This would analyze Terraform configurations to find dependencies
    // For this example, we'll return a simple dependency graph
    
    val dependencies = mutableMapOf<String, List<String>>()
    
    clouds.forEach { cloud ->
        val deps = when (cloud) {
            "aws" -> listOf("gcp") // AWS depends on GCP for some resources
            "azure" -> listOf("aws") // Azure depends on AWS
            "gcp" -> emptyList() // GCP has no dependencies
            else -> emptyList()
        }.filter { it in clouds }
        
        if (deps.isNotEmpty()) {
            dependencies[cloud] = deps
        }
    }
    
    return dependencies
}

fun resolveDependencyOrder(dependencies: Map<String, List<String>>): List<String> {
    // Simple topological sort
    val visited = mutableSetOf<String>()
    val result = mutableListOf<String>()
    
    fun visit(cloud: String) {
        if (cloud in visited) return
        
        dependencies[cloud]?.forEach { dep ->
            visit(dep)
        }
        
        visited.add(cloud)
        result.add(cloud)
    }
    
    // Visit all clouds
    val allClouds = dependencies.keys + dependencies.values.flatten()
    allClouds.toSet().forEach { visit(it) }
    
    return result
}
```

Estos ejemplos avanzados demuestran:

### 🏗️ **Características Empresariales**
- **Microservices Orchestration** - Deployment complejo multi-servicio
- **Multi-Cloud Management** - Gestión de infraestructura cross-cloud  
- **Security & Compliance** - Gates de calidad automatizados
- **Cost Management** - Control y optimización de costos
- **Disaster Recovery** - Rollbacks automáticos y recuperación

### ⚡ **Patrones Avanzados**
- **Dynamic Pipeline Generation** - Stages generados según configuración
- **Dependency Resolution** - Orden de deployment basado en dependencias
- **State Management** - Manejo de estados complejos
- **Cross-Stage Communication** - Paso de datos entre stages
- **Event-Driven Actions** - Respuestas automáticas a eventos

### 🛡️ **Enterprise Security**
- **Multi-Layer Scanning** - Security, compliance, cost scanning
- **Approval Gates** - Controles manuales para production
- **Audit Trails** - Logging completo de todas las acciones
- **Role-Based Access** - Controles de acceso granular

Estos pipelines representan casos de uso reales en entornos empresariales complejos.