# Sistema de Plugins - Hodei Pipeline DSL

## Resumen Ejecutivo

El sistema de plugins de Hodei Pipeline DSL permite **extensibilidad dinámica** con **carga en runtime**, **generación automática de DSL**, y **aislamiento de seguridad** mediante ClassLoaders especializados. Los plugins pueden agregar nuevos steps, agentes, y extensiones DSL manteniendo **type-safety completa**.

## Arquitectura del Sistema de Plugins

### 🏗️ **Componentes Principales**

```
┌─────────────────────────────────────────────┐
│                PLUGIN SYSTEM                │
├─────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────────────┐   │
│  │   Plugin    │  │     Extension       │   │
│  │   Manager   │  │    Container        │   │
│  └─────────────┘  └─────────────────────┘   │
│           │                    │             │
│  ┌─────────────┐  ┌─────────────────────┐   │
│  │ ClassLoader │  │   DSL Code          │   │
│  │ Isolation   │  │   Generator         │   │
│  └─────────────┘  └─────────────────────┘   │
├─────────────────────────────────────────────┤
│              CORE DSL ENGINE                │
└─────────────────────────────────────────────┘
```

### 🔧 **Principios de Diseño**

- **Hot Loading**: Plugins cargables sin reiniciar el sistema
- **Type Safety**: DSL generado con validación compile-time
- **Security Isolation**: ClassLoader sandboxing por plugin
- **Dependency Management**: Resolución automática de dependencias
- **Version Compatibility**: Soporte multi-versión con compatibilidad

## Plugin API Core

### 1. **Plugin Interface**

#### Base Plugin Contract
```kotlin
/**
 * Interface base para todos los plugins del sistema.
 * Define el contrato fundamental de extensibilidad.
 */
interface PipelinePlugin<T : PluginAware> {
    
    /**
     * Identificador único del plugin
     * Formato: organization.name (ej: "docker.core", "kubernetes.deploy")
     */
    val id: String
    
    /**
     * Versión semántica del plugin
     */
    val version: SemanticVersion
    
    /**
     * Nombre display del plugin
     */
    val displayName: String
    
    /**
     * Descripción del plugin
     */
    val description: String
    
    /**
     * Plugin dependencies (opcional)
     */
    val dependencies: List<PluginDependency>
        get() = emptyList()
    
    /**
     * Versión mínima requerida del core
     */
    val minCoreVersion: SemanticVersion
    
    /**
     * Aplica el plugin al target especificado
     * @param target Objeto que recibirá las extensiones del plugin
     */
    fun apply(target: T)
    
    /**
     * Configuración del plugin (opcional)
     */
    fun configure(configuration: PluginConfiguration) {}
    
    /**
     * Schema de extensiones que este plugin proporciona
     * Utilizado para generación automática de DSL
     */
    fun getExtensionSchema(): ExtensionSchema
    
    /**
     * Inicialización del plugin (async)
     */
    suspend fun initialize(context: PluginContext) {}
    
    /**
     * Limpieza de recursos del plugin
     */
    suspend fun destroy() {}
    
    /**
     * Verificación de salud del plugin
     */
    suspend fun healthCheck(): PluginHealth = PluginHealth.Healthy
}

/**
 * Dependency specification para plugins
 */
data class PluginDependency(
    val pluginId: String,
    val versionRange: VersionRange,
    val optional: Boolean = false
)

/**
 * Configuración pasada al plugin durante apply
 */
interface PluginConfiguration {
    fun <T> get(key: String): T?
    fun <T> get(key: String, defaultValue: T): T
    fun set(key: String, value: Any)
    fun contains(key: String): Boolean
}

/**
 * Contexto proporcionado durante inicialización
 */
interface PluginContext {
    val coreVersion: SemanticVersion
    val workingDirectory: Path
    val configDirectory: Path
    val logger: PluginLogger
    val metrics: PluginMetrics
}
```

#### Plugin Types
```kotlin
/**
 * Plugin que extiende el DSL principal
 */
abstract class DSLExtensionPlugin : PipelinePlugin<Pipeline> {
    
    /**
     * Registra extensiones DSL en el pipeline
     */
    abstract fun registerExtensions(pipeline: Pipeline)
    
    override fun apply(target: Pipeline) {
        registerExtensions(target)
    }
}

/**
 * Plugin que agrega nuevos tipos de steps
 */
abstract class StepPlugin : PipelinePlugin<StepsBuilder> {
    
    /**
     * Registra nuevos steps en el builder
     */
    abstract fun registerSteps(builder: StepsBuilder)
    
    override fun apply(target: StepsBuilder) {
        registerSteps(target)
    }
}

/**
 * Plugin que agrega nuevos tipos de agentes
 */
abstract class AgentPlugin : PipelinePlugin<AgentBuilder> {
    
    /**
     * Registra nuevos tipos de agente
     */
    abstract fun registerAgentTypes(builder: AgentBuilder)
    
    override fun apply(target: AgentBuilder) {
        registerAgentTypes(target)
    }
}

/**
 * Plugin que agrega notificaciones
 */
abstract class NotificationPlugin : PipelinePlugin<PostBuilder> {
    
    /**
     * Registra nuevos tipos de notificación
     */
    abstract fun registerNotifications(builder: PostBuilder)
    
    override fun apply(target: PostBuilder) {
        registerNotifications(target)
    }
}
```

### 2. **Extension Schema System**

#### Schema Definition
```kotlin
/**
 * Esquema que define las extensiones que un plugin provee
 * Usado para generación automática de DSL type-safe
 */
data class ExtensionSchema(
    val name: String,
    val type: ExtensionType,
    val properties: List<PropertySchema> = emptyList(),
    val methods: List<MethodSchema> = emptyList(),
    val nestedExtensions: List<ExtensionSchema> = emptyList()
) {
    
    enum class ExtensionType {
        DSL_EXTENSION,      // Extensión del DSL principal
        STEP_PROVIDER,      // Proveedor de steps
        AGENT_PROVIDER,     // Proveedor de agentes
        NOTIFICATION_PROVIDER // Proveedor de notificaciones
    }
}

/**
 * Schema de propiedades configurables
 */
data class PropertySchema(
    val name: String,
    val type: KClass<*>,
    val required: Boolean = false,
    val defaultValue: Any? = null,
    val description: String = "",
    val validation: PropertyValidation? = null
)

/**
 * Schema de métodos disponibles
 */
data class MethodSchema(
    val name: String,
    val parameters: List<ParameterSchema> = emptyList(),
    val returnType: KClass<*> = Unit::class,
    val description: String = "",
    val examples: List<String> = emptyList()
)

/**
 * Schema de parámetros de método
 */
data class ParameterSchema(
    val name: String,
    val type: KClass<*>,
    val required: Boolean = true,
    val defaultValue: Any? = null,
    val description: String = ""
)

/**
 * Validación de propiedades
 */
sealed class PropertyValidation {
    data class Range(val min: Number, val max: Number) : PropertyValidation()
    data class Pattern(val regex: Regex) : PropertyValidation()
    data class Enum(val values: List<String>) : PropertyValidation()
    data class Custom(val validator: (Any) -> ValidationResult) : PropertyValidation()
}
```

### 3. **Plugin Manager**

#### Core Plugin Management
```kotlin
/**
 * Gestor principal del sistema de plugins
 * Maneja carga, dependencias, y ciclo de vida de plugins
 */
interface PluginManager {
    
    /**
     * Carga plugin desde JAR file
     */
    suspend fun loadPlugin(pluginJar: Path): PluginDescriptor
    
    /**
     * Carga plugin desde coordinates Maven
     */
    suspend fun loadPlugin(coordinates: MavenCoordinates): PluginDescriptor
    
    /**
     * Descarga e instala plugin desde repository
     */
    suspend fun installPlugin(
        pluginId: String, 
        version: SemanticVersion,
        repository: PluginRepository = defaultRepository
    ): PluginDescriptor
    
    /**
     * Desinstala plugin
     */
    suspend fun uninstallPlugin(pluginId: String): Boolean
    
    /**
     * Lista plugins instalados
     */
    fun getInstalledPlugins(): List<PluginDescriptor>
    
    /**
     * Obtiene plugin por ID
     */
    fun getPlugin(pluginId: String): PluginDescriptor?
    
    /**
     * Verifica dependencias de plugin
     */
    fun validateDependencies(pluginId: String): DependencyValidationResult
    
    /**
     * Recarga plugin (hot reload)
     */
    suspend fun reloadPlugin(pluginId: String): PluginDescriptor
    
    /**
     * Genera bindings DSL para todos los plugins
     */
    suspend fun generateDSLBindings(): DSLGenerationResult
    
    /**
     * Estadísticas del sistema de plugins
     */
    fun getStats(): PluginManagerStats
}

/**
 * Implementación del Plugin Manager
 */
class DefaultPluginManager(
    private val codeGenerator: DSLCodeGenerator,
    private val scriptCompiler: ScriptCompiler,
    private val config: PluginManagerConfig
) : PluginManager {
    
    private val plugins = ConcurrentHashMap<String, LoadedPlugin>()
    private val classLoaders = ConcurrentHashMap<String, PluginClassLoader>()
    
    override suspend fun loadPlugin(pluginJar: Path): PluginDescriptor {
        val manifest = readPluginManifest(pluginJar)
        
        // Validar dependencias
        val depResult = validateDependencies(manifest.id)
        if (!depResult.isValid) {
            throw PluginLoadException("Dependencies not satisfied: ${depResult.errors}")
        }
        
        // Crear ClassLoader aislado
        val classLoader = createPluginClassLoader(pluginJar, manifest.id)
        
        // Cargar clase principal del plugin
        val pluginClass = classLoader.loadClass(manifest.mainClass)
        val plugin = pluginClass.getDeclaredConstructor().newInstance() as PipelinePlugin<*>
        
        // Inicializar plugin
        val context = createPluginContext(manifest)
        plugin.initialize(context)
        
        // Generar DSL bindings
        val schema = plugin.getExtensionSchema()
        generateDSLBindings(schema, manifest.id)
        
        // Recompilar scripts con nuevos bindings
        scriptCompiler.invalidateCache()
        
        val descriptor = PluginDescriptor(
            id = manifest.id,
            version = manifest.version,
            displayName = manifest.displayName,
            description = manifest.description,
            plugin = plugin,
            manifest = manifest,
            classLoader = classLoader,
            loadedAt = Instant.now()
        )
        
        plugins[manifest.id] = LoadedPlugin(descriptor, schema)
        classLoaders[manifest.id] = classLoader
        
        return descriptor
    }
    
    private fun createPluginClassLoader(
        pluginJar: Path, 
        pluginId: String
    ): PluginClassLoader {
        val parentClassLoader = createFilteredParentClassLoader()
        
        return PluginClassLoader(
            urls = arrayOf(pluginJar.toUri().toURL()),
            parent = parentClassLoader,
            pluginId = pluginId,
            securityPolicy = config.securityPolicy
        )
    }
    
    private suspend fun generateDSLBindings(
        schema: ExtensionSchema,
        pluginId: String
    ) {
        val bindingCode = codeGenerator.generateDSLExtensions(schema, pluginId)
        
        // Escribir código generado
        val outputDir = config.generatedSourcesDir.resolve(pluginId)
        Files.createDirectories(outputDir)
        
        bindingCode.writeTo(outputDir)
        
        // Actualizar script compilation config
        updateScriptImports(pluginId)
    }
}
```

### 4. **ClassLoader Isolation**

#### Secure Plugin ClassLoader
```kotlin
/**
 * ClassLoader especializado para plugins con aislamiento de seguridad
 */
class PluginClassLoader(
    urls: Array<URL>,
    parent: ClassLoader?,
    private val pluginId: String,
    private val securityPolicy: PluginSecurityPolicy
) : URLClassLoader(urls, parent) {
    
    private val loadedClasses = ConcurrentHashMap<String, Class<*>>()
    
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Cache check
        loadedClasses[name]?.let { return it }
        
        // Security check
        if (securityPolicy.isClassRestricted(pluginId, name)) {
            throw SecurityException("Plugin $pluginId cannot access class $name")
        }
        
        return when {
            // Plugin classes - load from plugin JAR first
            name.startsWith("plugin.$pluginId.") -> {
                findClass(name).also { 
                    loadedClasses[name] = it 
                }
            }
            
            // Core API classes - delegate to parent
            isApiClass(name) -> {
                parent?.loadClass(name) ?: throw ClassNotFoundException(name)
            }
            
            // Standard library classes
            isStandardLibraryClass(name) -> {
                super.loadClass(name, resolve)
            }
            
            // Restricted classes
            else -> {
                if (securityPolicy.isClassAllowed(pluginId, name)) {
                    super.loadClass(name, resolve)
                } else {
                    throw SecurityException("Access denied to class $name for plugin $pluginId")
                }
            }
        }
    }
    
    override fun findResource(name: String): URL? {
        // Security check for resources
        if (securityPolicy.isResourceRestricted(pluginId, name)) {
            return null
        }
        return super.findResource(name)
    }
    
    private fun isApiClass(className: String): Boolean {
        return className.startsWith("hodei.api.") ||
               className.startsWith("hodei.dsl.") ||
               className.startsWith("kotlin.") ||
               className.startsWith("kotlinx.coroutines.")
    }
    
    private fun isStandardLibraryClass(className: String): Boolean {
        return className.startsWith("java.") ||
               className.startsWith("javax.") ||
               className.startsWith("org.slf4j.")
    }
    
    /**
     * Limpieza de recursos del ClassLoader
     */
    fun cleanup() {
        loadedClasses.clear()
        try {
            close()
        } catch (e: IOException) {
            // Log warning pero continuar
            logger.warn("Error closing plugin ClassLoader for $pluginId", e)
        }
    }
}

/**
 * Política de seguridad para plugins
 */
interface PluginSecurityPolicy {
    
    /**
     * Verifica si una clase está restringida para el plugin
     */
    fun isClassRestricted(pluginId: String, className: String): Boolean
    
    /**
     * Verifica si una clase está permitida para el plugin
     */
    fun isClassAllowed(pluginId: String, className: String): Boolean
    
    /**
     * Verifica si un recurso está restringido
     */
    fun isResourceRestricted(pluginId: String, resourceName: String): Boolean
    
    /**
     * Verifica permisos de sistema
     */
    fun hasSystemPermission(pluginId: String, permission: String): Boolean
}

/**
 * Política de seguridad por defecto
 */
class DefaultPluginSecurityPolicy : PluginSecurityPolicy {
    
    private val restrictedClasses = setOf(
        "java.lang.System",
        "java.lang.Runtime",
        "java.lang.Process",
        "java.io.FileSystem",
        "java.net.URL",
        "java.security.*"
    )
    
    private val restrictedPackages = setOf(
        "sun.",
        "com.sun.",
        "jdk.internal.",
        "java.lang.reflect."
    )
    
    override fun isClassRestricted(pluginId: String, className: String): Boolean {
        return className in restrictedClasses || 
               restrictedPackages.any { className.startsWith(it) }
    }
    
    override fun isClassAllowed(pluginId: String, className: String): Boolean {
        return !isClassRestricted(pluginId, className)
    }
    
    override fun isResourceRestricted(pluginId: String, resourceName: String): Boolean {
        return resourceName.startsWith("/etc/") ||
               resourceName.startsWith("/var/") ||
               resourceName.contains("secret") ||
               resourceName.contains("password")
    }
    
    override fun hasSystemPermission(pluginId: String, permission: String): Boolean {
        // Por defecto, plugins no tienen permisos de sistema
        return false
    }
}
```

### 5. **DSL Code Generation**

#### Automatic DSL Generation
```kotlin
/**
 * Generador de código DSL para plugins
 * Convierte Extension Schema a código Kotlin type-safe
 */
interface DSLCodeGenerator {
    
    /**
     * Genera extensiones DSL para un plugin
     */
    fun generateDSLExtensions(
        schema: ExtensionSchema, 
        pluginId: String
    ): FileSpec
    
    /**
     * Genera clases de configuración
     */
    fun generateConfigurationClasses(
        schema: ExtensionSchema,
        pluginId: String  
    ): List<FileSpec>
    
    /**
     * Genera documentación DSL
     */
    fun generateDSLDocumentation(
        schema: ExtensionSchema,
        pluginId: String
    ): MarkdownFile
}

/**
 * Implementación con KotlinPoet
 */
class KotlinPoetDSLGenerator : DSLCodeGenerator {
    
    override fun generateDSLExtensions(
        schema: ExtensionSchema, 
        pluginId: String
    ): FileSpec {
        val packageName = "hodei.plugins.$pluginId.generated"
        val fileName = "${schema.name.capitalize()}Extensions"
        
        return FileSpec.builder(packageName, fileName)
            .addFileComment("Auto-generated DSL extensions for plugin: $pluginId")
            .addImport("hodei.dsl", "*")
            .apply {
                // Generar extension functions
                schema.methods.forEach { method ->
                    addFunction(generateExtensionFunction(method, schema))
                }
                
                // Generar configuration classes
                if (schema.properties.isNotEmpty()) {
                    addType(generateConfigurationClass(schema))
                }
                
                // Generar nested extensions
                schema.nestedExtensions.forEach { nested ->
                    addType(generateNestedExtensionClass(nested))
                }
            }
            .build()
    }
    
    private fun generateExtensionFunction(
        method: MethodSchema,
        schema: ExtensionSchema
    ): FunSpec {
        return FunSpec.builder(method.name)
            .receiver(determineReceiverType(schema.type))
            .apply {
                // Agregar parámetros
                method.parameters.forEach { param ->
                    addParameter(
                        ParameterSpec.builder(param.name, param.type.asClassName())
                            .apply {
                                if (!param.required && param.defaultValue != null) {
                                    defaultValue(generateDefaultValue(param.defaultValue))
                                }
                            }
                            .build()
                    )
                }
                
                // Agregar documentación
                if (method.description.isNotEmpty()) {
                    addKdoc(method.description)
                }
                
                // Generar cuerpo de función
                addStatement("return ${generateMethodCall(method, schema)}")
            }
            .build()
    }
    
    private fun generateConfigurationClass(schema: ExtensionSchema): TypeSpec {
        return TypeSpec.classBuilder("${schema.name.capitalize()}Configuration")
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .apply {
                        schema.properties.forEach { prop ->
                            addParameter(
                                ParameterSpec.builder(prop.name, prop.type.asClassName())
                                    .apply {
                                        if (prop.defaultValue != null) {
                                            defaultValue(generateDefaultValue(prop.defaultValue))
                                        }
                                    }
                                    .build()
                            )
                        }
                    }
                    .build()
            )
            .apply {
                schema.properties.forEach { prop ->
                    addProperty(
                        PropertySpec.builder(prop.name, prop.type.asClassName())
                            .initializer(prop.name)
                            .apply {
                                if (prop.description.isNotEmpty()) {
                                    addKdoc(prop.description)
                                }
                            }
                            .build()
                    )
                }
            }
            .build()
    }
    
    private fun determineReceiverType(extensionType: ExtensionSchema.ExtensionType): ClassName {
        return when (extensionType) {
            ExtensionSchema.ExtensionType.DSL_EXTENSION -> 
                ClassName("hodei.dsl", "Pipeline")
            ExtensionSchema.ExtensionType.STEP_PROVIDER -> 
                ClassName("hodei.dsl", "StepsBuilder")  
            ExtensionSchema.ExtensionType.AGENT_PROVIDER -> 
                ClassName("hodei.dsl", "AgentBuilder")
            ExtensionSchema.ExtensionType.NOTIFICATION_PROVIDER -> 
                ClassName("hodei.dsl", "PostBuilder")
        }
    }
}
```

### 6. **Plugin Discovery & Repository**

#### Plugin Repository System
```kotlin
/**
 * Repository para descubrimiento e instalación de plugins
 */
interface PluginRepository {
    
    /**
     * Busca plugins disponibles
     */
    suspend fun searchPlugins(query: String): List<PluginInfo>
    
    /**
     * Obtiene información de plugin específico
     */
    suspend fun getPluginInfo(pluginId: String): PluginInfo?
    
    /**
     * Lista versiones disponibles de plugin
     */
    suspend fun getAvailableVersions(pluginId: String): List<SemanticVersion>
    
    /**
     * Descarga plugin
     */
    suspend fun downloadPlugin(
        pluginId: String, 
        version: SemanticVersion
    ): Path
    
    /**
     * Verifica integridad de plugin
     */
    suspend fun verifyPlugin(pluginFile: Path): PluginVerificationResult
}

/**
 * Repository basado en Maven
 */
class MavenPluginRepository(
    private val repositoryUrl: String,
    private val httpClient: HttpClient
) : PluginRepository {
    
    override suspend fun searchPlugins(query: String): List<PluginInfo> {
        val searchUrl = "$repositoryUrl/solr/select?q=$query+AND+packaging:hodei-plugin"
        
        val response = httpClient.get(searchUrl)
        val searchResult = Json.decodeFromString<MavenSearchResult>(response.body())
        
        return searchResult.response.docs.map { doc ->
            PluginInfo(
                id = doc.g + "." + doc.a,
                groupId = doc.g,
                artifactId = doc.a,
                name = doc.a,
                description = "",
                latestVersion = SemanticVersion.parse(doc.latestVersion),
                repository = this
            )
        }
    }
    
    override suspend fun downloadPlugin(
        pluginId: String, 
        version: SemanticVersion
    ): Path {
        val (groupId, artifactId) = parsePluginId(pluginId)
        val downloadUrl = buildDownloadUrl(groupId, artifactId, version)
        
        val response = httpClient.get(downloadUrl)
        val tempFile = Files.createTempFile("plugin-", ".jar")
        
        Files.write(tempFile, response.body())
        
        return tempFile
    }
    
    private fun buildDownloadUrl(
        groupId: String, 
        artifactId: String, 
        version: SemanticVersion
    ): String {
        val groupPath = groupId.replace('.', '/')
        return "$repositoryUrl/$groupPath/$artifactId/$version/$artifactId-$version.jar"
    }
}

/**
 * Registry local de plugins
 */
class LocalPluginRegistry(
    private val registryFile: Path
) {
    
    private val plugins = mutableMapOf<String, PluginRegistryEntry>()
    
    init {
        loadRegistry()
    }
    
    fun registerPlugin(descriptor: PluginDescriptor) {
        plugins[descriptor.id] = PluginRegistryEntry(
            id = descriptor.id,
            version = descriptor.version,
            installPath = descriptor.installPath,
            installedAt = Instant.now(),
            enabled = true
        )
        saveRegistry()
    }
    
    fun unregisterPlugin(pluginId: String) {
        plugins.remove(pluginId)
        saveRegistry()
    }
    
    fun getRegisteredPlugins(): List<PluginRegistryEntry> {
        return plugins.values.toList()
    }
    
    fun isPluginRegistered(pluginId: String): Boolean {
        return plugins.containsKey(pluginId)
    }
    
    private fun loadRegistry() {
        if (Files.exists(registryFile)) {
            val json = Files.readString(registryFile)
            val entries = Json.decodeFromString<List<PluginRegistryEntry>>(json)
            entries.forEach { plugins[it.id] = it }
        }
    }
    
    private fun saveRegistry() {
        val json = Json.encodeToString(plugins.values.toList())
        Files.writeString(registryFile, json)
    }
}

data class PluginRegistryEntry(
    val id: String,
    val version: SemanticVersion,
    val installPath: Path,
    val installedAt: Instant,
    val enabled: Boolean
)
```

### 7. **Plugin Examples**

#### Docker Plugin Implementation
```kotlin
/**
 * Plugin que agrega soporte para Docker
 */
class DockerPlugin : DSLExtensionPlugin() {
    
    override val id = "docker.core"
    override val version = SemanticVersion(1, 0, 0)
    override val displayName = "Docker Plugin"
    override val description = "Provides Docker integration for pipeline steps"
    override val minCoreVersion = SemanticVersion(1, 0, 0)
    
    override fun registerExtensions(pipeline: Pipeline) {
        // Registrar extensión docker en el pipeline
        pipeline.extensions.create("docker") {
            DockerExtension(pipeline.context)
        }
    }
    
    override fun getExtensionSchema(): ExtensionSchema {
        return ExtensionSchema(
            name = "docker",
            type = ExtensionSchema.ExtensionType.DSL_EXTENSION,
            methods = listOf(
                MethodSchema(
                    name = "build",
                    parameters = listOf(
                        ParameterSchema("tag", String::class),
                        ParameterSchema("dockerfile", String::class, false, "Dockerfile"),
                        ParameterSchema("context", String::class, false, ".")
                    ),
                    description = "Build Docker image"
                ),
                MethodSchema(
                    name = "push",
                    parameters = listOf(
                        ParameterSchema("image", String::class),
                        ParameterSchema("registry", String::class, false)
                    ),
                    description = "Push Docker image to registry"
                )
            ),
            properties = listOf(
                PropertySchema(
                    name = "registry",
                    type = String::class,
                    description = "Default Docker registry"
                )
            )
        )
    }
}

/**
 * Extensión Docker generada automáticamente por el sistema
 */
class DockerExtension(private val context: ExecutionContext) {
    var registry: String = "docker.io"
    
    suspend fun build(
        tag: String, 
        dockerfile: String = "Dockerfile",
        context: String = "."
    ): DockerImage {
        val buildCommand = "docker build -t $tag -f $dockerfile $context"
        
        val result = ShellStep(buildCommand).execute(context)
        
        return when (result) {
            is StepResult.Success -> DockerImage(tag)
            is StepResult.Failure -> throw DockerBuildException(result.message)
            else -> throw DockerBuildException("Unexpected build result")
        }
    }
    
    suspend fun push(image: String, registry: String? = null): PushResult {
        val targetRegistry = registry ?: this.registry
        val fullImageName = if (registry != null) "$registry/$image" else image
        
        val pushCommand = "docker push $fullImageName"
        val result = ShellStep(pushCommand).execute(context)
        
        return when (result) {
            is StepResult.Success -> PushResult.Success(fullImageName)
            is StepResult.Failure -> PushResult.Failed(result.message)
            else -> PushResult.Failed("Unexpected push result")
        }
    }
}

// DSL Extensions generadas automáticamente
fun Pipeline.docker(configure: DockerExtension.() -> Unit) {
    extensions.configure("docker", configure)
}

fun StepsBuilder.dockerBuild(
    tag: String, 
    dockerfile: String = "Dockerfile",
    context: String = "."
) {
    steps.add(DockerBuildStep(tag, dockerfile, context))
}

fun StepsBuilder.dockerPush(image: String, registry: String? = null) {
    steps.add(DockerPushStep(image, registry))
}
```

#### Slack Notification Plugin
```kotlin
class SlackPlugin : NotificationPlugin() {
    
    override val id = "notifications.slack"
    override val version = SemanticVersion(1, 2, 0)
    override val displayName = "Slack Notifications"
    override val description = "Send notifications to Slack channels"
    
    override fun registerNotifications(builder: PostBuilder) {
        builder.registerNotification("slack") { config ->
            SlackNotificationStep(config as SlackConfig)
        }
    }
    
    override fun getExtensionSchema(): ExtensionSchema {
        return ExtensionSchema(
            name = "slack",
            type = ExtensionSchema.ExtensionType.NOTIFICATION_PROVIDER,
            methods = listOf(
                MethodSchema(
                    name = "notify",
                    parameters = listOf(
                        ParameterSchema("channel", String::class),
                        ParameterSchema("message", String::class),
                        ParameterSchema("color", String::class, false, "good"),
                        ParameterSchema("username", String::class, false, "Pipeline Bot")
                    )
                )
            ),
            properties = listOf(
                PropertySchema(
                    name = "webhookUrl",
                    type = String::class,
                    required = true,
                    description = "Slack webhook URL"
                ),
                PropertySchema(
                    name = "defaultChannel",
                    type = String::class,
                    defaultValue = "#general"
                )
            )
        )
    }
}

// Uso en pipeline
pipeline {
    post {
        success {
            slack {
                channel = "#deployments"
                message = "Deployment successful! ✅"
                color = "good"
            }
        }
        
        failure {
            slack {
                channel = "#alerts"
                message = "Build failed! ❌"
                color = "danger"
            }
        }
    }
}
```

### 8. **Plugin Configuration**

#### Plugin Manifest
```yaml
# plugin.yaml - Plugin manifest file
id: docker.core
version: 1.0.0
name: Docker Plugin
description: Provides Docker integration for pipelines
author: Hodei Team
website: https://github.com/hodei/plugins/docker
license: MIT

# Core requirements
minCoreVersion: 1.0.0
maxCoreVersion: 2.0.0

# Plugin main class
mainClass: docker.core.DockerPlugin

# Dependencies
dependencies:
  - id: utils.shell
    version: "^1.0.0"
    optional: false

# Permissions required
permissions:
  - docker.api
  - file.read
  - network.access

# Configuration schema
configuration:
  properties:
    defaultRegistry:
      type: string
      default: "docker.io"
      description: "Default Docker registry"
    
    timeoutSeconds:
      type: integer
      default: 300
      min: 60
      max: 3600
      description: "Default timeout for Docker operations"
    
    enableBuildKit:
      type: boolean
      default: true
      description: "Enable Docker BuildKit"

# Resources bundled with plugin
resources:
  - templates/Dockerfile.base
  - scripts/docker-health-check.sh
```

#### Runtime Configuration
```kotlin
// Configuración durante instalación/carga
val dockerPluginConfig = PluginConfiguration.builder()
    .set("defaultRegistry", "company-registry.io")
    .set("timeoutSeconds", 600)
    .set("enableBuildKit", true)
    .build()

pluginManager.loadPlugin("docker.core", dockerPluginConfig)
```

---

Este sistema de plugins proporciona:

- **🔌 Extensibilidad Completa**: Plugins pueden agregar cualquier funcionalidad
- **🛡️ Seguridad Robusta**: ClassLoader isolation y sandboxing
- **⚡ Type-Safety**: DSL generado automáticamente con validación
- **🚀 Hot Loading**: Carga/descarga sin reiniciar el sistema
- **📦 Gestión Simple**: Discovery, instalación y dependencias automáticas
- **🔧 API Flexible**: Múltiples tipos de plugins con extensión points claros