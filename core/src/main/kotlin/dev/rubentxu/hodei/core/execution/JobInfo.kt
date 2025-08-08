package dev.rubentxu.hodei.core.execution

/**
 * Job information for pipeline execution
 * 
 * Contains metadata about the current job/pipeline execution including
 * build information, Git details, and job parameters.
 */
public data class JobInfo(
    /**
     * Name of the job/pipeline
     */
    val jobName: String,
    
    /**
     * Current build number
     */
    val buildNumber: String,
    
    /**
     * URL to the build in CI system
     */
    val buildUrl: String,
    
    /**
     * Git commit SHA for this build
     */
    val gitCommit: String? = null,
    
    /**
     * Git branch being built
     */
    val gitBranch: String? = null,
    
    /**
     * Git repository URL
     */
    val gitUrl: String? = null,
    
    /**
     * Job/build parameters
     */
    val parameters: Map<String, String> = emptyMap(),
    
    /**
     * Trigger information (manual, SCM, timer, etc.)
     */
    val trigger: String? = null,
    
    /**
     * User who triggered the build
     */
    val triggeredBy: String? = null
) {
    
    init {
        require(jobName.isNotBlank()) { "Job name cannot be blank" }
        require(buildNumber.isNotBlank()) { "Build number cannot be blank" }
        require(buildUrl.isNotBlank()) { "Build URL cannot be blank" }
    }
    
    /**
     * Gets a parameter value by name
     * @param name Parameter name
     * @return Parameter value or null if not found
     */
    public fun getParameter(name: String): String? = parameters[name]
    
    /**
     * Gets a parameter value with default
     * @param name Parameter name
     * @param default Default value if parameter not found
     * @return Parameter value or default
     */
    public fun getParameter(name: String, default: String): String = parameters[name] ?: default
    
    /**
     * Checks if this build is triggered by Git SCM
     */
    public val isGitBuild: Boolean
        get() = gitCommit != null && gitBranch != null
    
    /**
     * Checks if this build is from a pull/merge request
     */
    public val isPullRequest: Boolean
        get() = gitBranch?.startsWith("PR-") == true || gitBranch?.startsWith("MR-") == true
    
    public companion object {
        /**
         * Creates default job info for testing
         */
        public fun default(): JobInfo = JobInfo(
            jobName = "test-pipeline",
            buildNumber = "1",
            buildUrl = "http://localhost:8080/job/test-pipeline/1/",
            gitCommit = "abc123def456789",
            gitBranch = "main",
            gitUrl = "https://github.com/example/repo.git",
            triggeredBy = "test-user"
        )
        
        /**
         * Creates job info for development environment
         */
        public fun development(): JobInfo = JobInfo(
            jobName = "dev-pipeline",
            buildNumber = "dev-${System.currentTimeMillis()}",
            buildUrl = "http://localhost:8080/job/dev-pipeline/1/",
            gitCommit = "dev-commit",
            gitBranch = "develop",
            parameters = mapOf("ENVIRONMENT" to "development"),
            trigger = "manual",
            triggeredBy = System.getProperty("user.name")
        )
        
        /**
         * Creates job info from environment variables
         * Compatible with common CI/CD systems (Jenkins, GitHub Actions, etc.)
         */
        public fun fromEnvironment(): JobInfo {
            val env = System.getenv()
            return JobInfo(
                jobName = env["JOB_NAME"] ?: env["GITHUB_WORKFLOW"] ?: "unknown-job",
                buildNumber = env["BUILD_NUMBER"] ?: env["GITHUB_RUN_NUMBER"] ?: "1",
                buildUrl = env["BUILD_URL"] ?: env["GITHUB_SERVER_URL"]?.let { "$it/${env["GITHUB_REPOSITORY"]}/actions/runs/${env["GITHUB_RUN_ID"]}" } ?: "unknown-url",
                gitCommit = env["GIT_COMMIT"] ?: env["GITHUB_SHA"],
                gitBranch = env["GIT_BRANCH"] ?: env["GITHUB_REF_NAME"],
                gitUrl = env["GIT_URL"] ?: env["GITHUB_SERVER_URL"]?.let { "$it/${env["GITHUB_REPOSITORY"]}.git" },
                triggeredBy = env["BUILD_USER"] ?: env["GITHUB_ACTOR"]
            )
        }
    }
}