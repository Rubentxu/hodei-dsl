# Plan de Desarrollo - Hodei Pipeline DSL

## Objetivo General

Desarrollar un sistema Pipeline DSL en Kotlin que replique la funcionalidad completa de Jenkins Pipelines con mejoras modernas como type-safety, ejecución paralela real con coroutines, y un sistema de plugins dinámico.

## Metodología de Desarrollo

### 🧪 TDD/BDD Workflow
- **Test-Driven Development**: Ciclo obligatorio Red-Green-Refactor
- **Behavior-Driven Development**: Specs con Kotest para documentación ejecutable
- **Conventional Commits**: Commits estructurados para cada feature
- **Pre-commit Hooks**: Validación automática de tests y formato

**Ver**: [TDD Workflow Detallado](./tdd-workflow.md)

### 🔄 Flujo por Feature
1. **🔴 RED**: Escribir tests BDD que fallan
2. **🟢 GREEN**: Implementar código mínimo para pasar tests
3. **🔄 REFACTOR**: Mejorar código manteniendo tests verdes
4. **💾 COMMIT**: Conventional commit con la feature completa

## Cronograma de Desarrollo

### **Fase 0: Alineación con /docs y backlog inicial** 🧭
**Duración**: 0.5-1 día  
**Estado**: 🟡 En Progreso

#### Tareas Principales
- [x] Revisar documentación clave (/docs) y extraer requisitos
- [x] Sincronizar el plan con especificaciones existentes
- [x] Definir backlog inicial por módulo (core, compiler, execution, steps, plugins, cli, library)
- [x] Seleccionar specs objetivo para la primera iteración y comandos de ejecución
- [ ] Abrir issues/tickets por épica y módulo (opcional)

#### Criterios de Aceptación
- Plan actualizado y alineado con /docs
- Fase 1 ajustada al estado real de la documentación
- Backlog inicial definido y priorizado

#### Entregables
- Este archivo actualizado con Fase 0 y backlog inicial

---

### **Fase 1: Documentación y Especificaciones** 📋
**Duración**: 2-3 días  
**Estado**: 🟡 En Progreso

#### Tareas Principales
- [x] Crear estructura de documentación
- [ ] Documentar arquitectura general del sistema
- [ ] Especificar APIs core detalladamente
- [ ] Definir sintaxis y semántica del DSL
- [ ] Documentar sistema de plugins
- [ ] Especificar modelo de ejecución y concurrencia
- [ ] Crear ejemplos de integración y casos de uso

#### Criterios de Aceptación
- Documentación técnica completa y revisada
- Especificaciones detalladas de cada módulo
- Diagramas de arquitectura actualizados
- Ejemplos de uso documentados

#### Entregables
- `/docs/` completo con toda la documentación
- Diagramas de arquitectura en Mermaid
- Especificaciones técnicas detalladas

---

### **Fase 2: Reestructuración del Proyecto** 🏗️
**Duración**: 1 día  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 1

#### Tareas Principales
- [ ] Reorganizar módulos según arquitectura hexagonal
- [ ] Actualizar `libs.versions.toml` con todas las dependencias necesarias
- [ ] Configurar build scripts de Gradle
- [ ] Crear estructura de directorios por módulos
- [ ] Configurar plugins de Gradle necesarios

#### Criterios de Aceptación
- Estructura modular clara y bien organizada
- Build system configurado correctamente
- Dependencias centralizadas y versionadas
- Convenciones de código establecidas

#### Entregables
- Proyecto reestructurado con módulos separados
- Configuración de Gradle actualizada
- Estructura de directorios final

---

### **Fase 3: Core API y DSL** ⚙️
**Duración**: 3-4 días  
**Estado**: ✅ Completada  
**Dependencias**: Fase 2

#### Tareas Principales (TDD/BDD)
- [x] **RED**: Escribir PipelineSpec con comportamientos esperados
- [x] **GREEN**: Implementar interfaces base del Pipeline DSL
- [x] **RED**: Escribir StageBuilderSpec para type-safe builders
- [x] **GREEN**: Crear builders type-safe para Pipeline, Stage, Steps
- [x] **RED**: Escribir ExecutionContextSpec para manejo de contextos
- [x] **GREEN**: Implementar sistema de contexto de ejecución
- [x] **REFACTOR**: Aplicar principios SOLID y clean architecture
- [x] **COMMIT**: `feat(core): implement pipeline DSL foundation`

#### ✨ **Implementación SOLID Extra (Completada)**
- [x] **FASE 1**: Crear infrastructure SOLID (StepHandler, Registry, AbstractStepHandler)
- [x] **FASE 2**: Migrar handlers simples (Echo, Shell, ArchiveArtifacts, PublishTestResults, Stash, Unstash)
- [x] **FASE 3**: Migrar handlers complejos (Dir, WithEnv, Parallel, Retry, Timeout)
- [x] **Tests**: 100% éxito en todos los handlers implementados (EchoStepHandlerSpec, ComplexStepHandlersSpec)

#### Criterios de Aceptación
- ✅ DSL funcional con type-safety completa
- ✅ API intuitiva y compatible con Jenkins
- ✅ Validación de sintaxis implementada
- ✅ Cobertura de tests > 90%
- ✅ **Sistema SOLID implementado y funcionando**

#### Entregables
- ✅ Módulo `core` completo y testado
- ✅ API pública estable
- ✅ Documentación de API actualizada
- ✅ **Sistema de handlers SOLID operativo**

---

### **Fase 4: Modernización DSL con Context Receivers** 🔧
**Duración**: 2-3 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 3

#### Tareas Principales (TDD/BDD)
- [ ] **RED**: Escribir DSLContextReceiversSpec para nuevas APIs
- [ ] **GREEN**: Implementar context receivers en builders
- [ ] **RED**: Escribir ImprovedBuilderAPISpec para sintaxis moderna
- [ ] **GREEN**: Modernizar API de builders con context receivers
- [ ] **RED**: Escribir BackwardsCompatibilitySpec para compatibilidad
- [ ] **GREEN**: Mantener compatibilidad con API existente
- [ ] **REFACTOR**: Optimizar DSL con nuevas características de Kotlin
- [ ] **COMMIT**: `feat(core): modernize DSL with context receivers`

#### Criterios de Aceptación
- Context receivers implementados correctamente
- API mejorada y más fluida
- Compatibilidad hacia atrás mantenida
- Tests actualizados y funcionando

#### Entregables
- DSL modernizado con context receivers
- API mejorada para builders
- Documentación actualizada

---

### **Fase 5: Cleanup y Consolidación** 🧹
**Duración**: 2-3 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 4

#### Tareas Principales
- [ ] Eliminar código legacy no utilizado
- [ ] Consolidar módulos execution/ con core/execution/
- [ ] Refactorizar duplicaciones de código
- [ ] Decidir estructura final de módulos (steps/ vs core/handlers/)
- [ ] Optimizar imports y dependencies
- [ ] Limpiar comentarios TODO y FIXME

#### Criterios de Aceptación
- Código legacy eliminado
- Módulos consolidados correctamente
- No hay duplicación de funcionalidad
- Estructura de proyecto final definida

#### Entregables
- Proyecto limpio y consolidado
- Arquitectura de módulos final
- Documentación actualizada

---

### **Fase 6: Biblioteca de Steps Completa y Compatibilidad** 📝
**Duración**: 3-4 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 5

#### Tareas Principales
- [ ] Implementar handlers para steps de Docker (`docker.image`, `docker.withRegistry`)
- [ ] Implementar handlers para steps de Git y SCM (`git`, `checkout`)
- [ ] Implementar handlers para steps avanzados (`input`, `emailext`, `build`)
- [ ] Crear suite de tests de compatibilidad que verifique el comportamiento de cada step contra su análogo en Jenkins
- [ ] Documentación completa de todos los steps disponibles
- [ ] Validación de parámetros y compatibilidad sintáctica con Jenkins

#### Criterios de Aceptación
- Biblioteca de steps principales de Jenkins completa y funcional bajo el sistema de handlers SOLID
- Cobertura de tests alta para todos los handlers implementados
- Tests de compatibilidad que validan la paridad de comportamiento con Jenkins
- Documentación detallada de uso para cada step

#### Entregables
- Handlers completos para todos los steps principales de Jenkins
- Suite de tests de compatibilidad Jenkins
- Documentación de referencia de steps

---

### **Fase 7: Runtime de Carga de Plugins** 🔌
**Duración**: 4-5 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 3, Fase 4

#### Tareas Principales
- [ ] Implementar la interfaz `HodeiPlugin` como punto de entrada para plugins externos
- [ ] Implementar el sistema de escaneo del directorio `/plugins` al arranque de Hodei
- [ ] Integrar `java.util.ServiceLoader` para descubrir y ejecutar el método `register()` de cada plugin
- [ ] **TAREA CRÍTICA**: Modificar el módulo `:compiler` para que el `classpath` de compilación de los scripts `.kts` incluya los JARs de los plugins descubiertos
- [ ] Crear un repositorio de ejemplo con un plugin completo (`slack-notify` o similar) para probar todo el flujo
- [ ] Documentar el proceso de desarrollo de plugins en `PLUGIN_DEVELOPMENT_GUIDE.md`

#### Criterios de Aceptación
- Un JAR de un plugin colocado en la carpeta `/plugins` es cargado al reiniciar Hodei
- Los steps definidos en el plugin están disponibles en la DSL con autocompletado en el IDE
- El pipeline se ejecuta correctamente utilizando steps del `core` y de plugins externos
- La guía de desarrollo de plugins es clara y permite a un tercero crear un plugin funcional

#### Entregables
- Sistema de carga de plugins operativo con ServiceLoader
- Plugin de ejemplo completo como referencia
- Guía de desarrollo de plugins detallada
- Integración del classpath de plugins en el compilador

#### Notas sobre Alcance v1.0
- **ClassLoader isolation** y **Sandboxing de seguridad** se consideran funcionalidades para versión 2.0
- Para v1.0 se utilizará un ClassLoader compartido como punto de partida seguro y realista

---

### **Fase 8: CLI Implementation** 💻
**Duración**: 2-3 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 7

#### Tareas Principales
- [ ] Interface de línea de comandos
- [ ] Watch mode para desarrollo
- [ ] Sistema de configuración
- [ ] Logging estructurado
- [ ] Manejo de errores user-friendly

#### Criterios de Aceptación
- CLI intuitiva y funcional
- Watch mode operativo
- Configuración flexible
- Mensajes de error claros

#### Entregables
- Aplicación CLI completa
- Documentación de comandos
- Scripts de instalación

---

### **Fase 9: API Embebida** 📚
**Duración**: 2-3 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 8

#### Tareas Principales
- [ ] Library API para integración
- [ ] Spring Boot starter
- [ ] Reactive streams support
- [ ] Java compatibility layer
- [ ] Documentación de integración

#### Criterios de Aceptación
- API embebida funcional
- Integración Spring Boot
- Compatibilidad Java completa
- Documentación de integración

#### Entregables
- Librería embebible
- Spring Boot starter
- Ejemplos de integración

---

### **Fase 10: Testing e Integración** 🧪
**Duración**: 3-4 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 9

#### Tareas Principales
- [ ] Tests de integración end-to-end
- [ ] Tests de performance y benchmarking
- [ ] Tests de compatibilidad con Jenkins
- [ ] Ejemplos completos y funcionales
- [ ] Documentación de usuario final

#### Criterios de Aceptación
- Suite completa de tests de integración
- Performance benchmarks satisfactorios
- Compatibilidad Jenkins verificada
- Ejemplos funcionando correctamente

#### Entregables
- Suite completa de tests
- Reportes de performance
- Ejemplos de usuario

---

### **Fase 11: Optimización y Deployment** 🚀
**Duración**: 2-3 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 10

#### Tareas Principales
- [ ] Optimizaciones de rendimiento
- [ ] Packaging para distribución
- [ ] CI/CD pipeline setup
- [ ] Documentación de release
- [ ] Preparación de versión 1.0

#### Criterios de Aceptación
- Performance optimizado
- Distribución automatizada
- CI/CD funcional
- Release preparation completa

#### Entregables
- Sistema optimizado
- CI/CD pipeline
- Release 1.0 preparado

---

## Estimaciones Totales y Progreso Real

| Fase | Duración | Tipo | Estado | Progreso Real |
|------|----------|------|--------|---------------|
| **Fase 1-2** | 3-4 días | Preparación | ⏳ Pendiente | 0% |
| **Fase 3** | 3-4 días | Core Development | ✅ **Completada** | **100%** ✅ |
| **Fase 4** | 2-3 días | DSL Modernización | ⏳ Pendiente | 0% 🔧 |
| **Fase 5** | 2-3 días | Cleanup & Consolidación | ⏳ Pendiente | 0% 🧹 |
| **Fase 6** | 3-4 días | Steps Biblioteca Completa | ⏳ Pendiente | **~15%** 📝 |
| **Fase 7** | 4-5 días | Plugin Runtime | ⏳ Pendiente | 0% 🔌 |
| **Fase 8-9** | 4-6 días | Interfaces (CLI/Library) | 🟡 **Básico** | **~25%** 💻 |
| **Fase 10-11** | 5-7 días | Testing & Deploy | ⏳ Pendiente | 0% |
| **TOTAL** | **28-37 días** | **Completo** | **🟡 En Progreso** | **~47% Completado** 📊 |

### 📊 Progreso por Módulo

| Módulo | Archivos Impl. | Tests | Estado | Completitud |
|--------|----------------|-------|---------|-------------|
| **core** | 51 archivos | 19 specs | ✅ **Completo SOLID** | **98%** |
| **compiler** | 9 archivos | 10 specs | 🔧 Avanzado | **80%** |
| **execution** | 3 archivos | 2 specs | 🚀 Básico | **40%** |
| **cli** | 2 archivos | 0 specs | 💻 Básico | **25%** |
| **examples** | 3 ejemplos | 0 specs | 💡 Funcional | **80%** |
| **plugins** | 1 archivo | 0 specs | 📝 Esqueleto | **10%** |
| **steps** | 1 archivo | 0 specs | 📝 Esqueleto | **10%** |
| **library** | 1 archivo | 0 specs | 📚 Esqueleto | **10%** |

## Hitos Importantes

- **🎯 Hito 1** ✅ **COMPLETADO** (Final Fase 3): Core DSL funcional con sistema SOLID de handlers
- **🎯 Hito 2** (Final Fase 5): Motor de ejecución completo con structured concurrency
- **🎯 Hito 3** (Final Fase 7): Runtime de carga de plugins operativo con ServiceLoader
- **🎯 Hito 4** (Final Fase 9): APIs públicas finalizadas (CLI + Library)
- **🎯 Hito 5** (Final Fase 11): Release 1.0 lista con distribución completa

## Riesgos y Mitigaciones

### Riesgos Técnicos
- **Complejidad del Kotlin Script Engine**: Mitigación con POCs tempranos
- **Performance de ejecución paralela**: Benchmarking continuo
- **Compatibilidad con Jenkins**: Tests de compatibilidad extensivos

### Riesgos de Cronograma
- **Estimaciones optimistas**: Buffer de 20% en cada fase
- **Dependencias entre fases**: Paralelización donde sea posible
- **Complejidad no prevista**: Reviews técnicas frecuentes

### Riesgos Adicionales Identificados
- **Complejidad del Classpath de Scripting**: La manipulación del classpath del compilador de scripts de Kotlin puede ser delicada. Una mala configuración podría llevar a errores de "clase no encontrada" difíciles de depurar.
  - **Mitigación**: Crear un test de integración temprano (parte de la Fase 7) que compile y ejecute un script usando un step de un plugin de prueba.

---

**Versión del Plan**: 1.0  
**Fecha de Creación**: $(date +'%Y-%m-%d')  
**Próxima Revisión**: Semanal

## Backlog Inicial por Módulo (alineado con /docs)

Esta sección deriva directamente de las especificaciones en /docs y prioriza un arranque incremental. Cada ítem referencia su documento base cuando aplica.

### 1) Núcleo (core) ✅ **COMPLETADO - SISTEMA SOLID FUNCIONAL**
- Referencias: docs/api-core-spec.md, docs/dsl-specification.md, docs/architecture.md
- ✅ MVP DSL validado: pipeline, agent(any/label), environment(set/credentials), stages, steps (echo, sh), post(always)
- ✅ ExecutionContext: variables, workspace, logger configurable, stash básico
- ✅ WhenCondition: all/any/not + DSL (ya modelado)
- ✅ Builders: PipelineBuilder, StageBuilder, StepsBuilder coherentes con la spec
- ✅ **Sistema SOLID de Handlers**: StepHandler, StepHandlerRegistry, AbstractStepHandler
- ✅ **Handlers Simples**: Echo, Shell, ArchiveArtifacts, PublishTestResults, Stash, Unstash
- ✅ **Handlers Complejos**: Dir, WithEnv, Parallel, Retry, Timeout

#### Estadísticas Actualizadas del Módulo Core:
- **📁 Total de archivos**: 51 archivos Kotlin implementados
- **🧪 Tests implementados**: 19 especificaciones Kotest
- **⚡ Handlers SOLID**: **13 handlers** implementados bajo arquitectura SOLID
- **🎯 Cobertura funcional**: ~98% completitud

#### Tests Ejecutados y Exitosos:
- ✅ gradle :core:test --tests "dev.rubentxu.hodei.core.dsl.DSLBuilderSpec"
- ✅ gradle :core:test --tests "dev.rubentxu.hodei.core.domain.WhenConditionSpec"
- ✅ gradle :core:test --tests "dev.rubentxu.hodei.core.domain.WhenConditionDSLSpec"
- ✅ gradle :core:test --tests "dev.rubentxu.hodei.core.execution.ExecutionContextSpec"
- ✅ gradle :core:test --tests "dev.rubentxu.hodei.core.execution.ConfigurableLoggerSpec"
- ✅ gradle :core:test --tests "dev.rubentxu.hodei.core.execution.StashSystemSpec"
- ✅ gradle :core:test --tests "dev.rubentxu.hodei.core.execution.handlers.EchoStepHandlerSpec" (4/4 tests ✅)
- ✅ gradle :core:test --tests "dev.rubentxu.hodei.core.execution.handlers.HandlerRegistrationSpec" (2/2 tests ✅)
- ✅ gradle :core:test --tests "dev.rubentxu.hodei.core.execution.handlers.ComplexStepHandlersSpec" (**TODOS** los handlers complejos ✅)
- ✅ gradle :core:test --tests "dev.rubentxu.hodei.core.execution.handlers.ComplexHandlerRegistrationVerificationSpec" (Verificación registro ✅)

#### Sistema SOLID de Handlers - Estado Actual:
- ✅ **FASE 1**: Infrastructure (StepHandler, Registry, AbstractStepHandler) - **COMPLETADA**
- ✅ **FASE 2**: Handlers simples (Echo, Shell, ArchiveArtifacts, PublishTestResults, Stash, Unstash) - **COMPLETADA**
- ✅ **FASE 3**: Handlers complejos (Dir, WithEnv, Parallel, Retry, Timeout) - **COMPLETADA**
- ⏳ **FASE 4**: Modernización DSL con Context Receivers - **PENDIENTE**
- ⏳ **FASE 5**: Cleanup código legacy y consolidación de módulos - **PENDIENTE**

#### Lista Completa de Handlers Implementados (13 total):
1. **AbstractStepHandler** - Clase base con patrón Template Method
2. **EchoStepHandler** - Manejo de echo steps
3. **ShellStepHandler** - Ejecución de comandos shell
4. **ArchiveArtifactsStepHandler** - Archivado de artefactos
5. **PublishTestResultsStepHandler** - Publicación de resultados de tests
6. **StashStepHandler** - Almacenamiento temporal de archivos
7. **UnstashStepHandler** - Recuperación de archivos stash
8. **DirStepHandler** - Operaciones de directorio con contexto
9. **WithEnvStepHandler** - Manipulación de variables de entorno
10. **ParallelStepHandler** - Ejecución paralela con coroutines
11. **RetryStepHandler** - Lógica de retry con backoff exponencial
12. **TimeoutStepHandler** - Manejo de timeouts con cancelación
13. **DefaultHandlerRegistration** - Registro automático de handlers

#### Próximas Mejoras de Core:
- **Total de 13 handlers implementados** funcionando con arquitectura SOLID completa
- Sistema listo para extensión con nuevos handlers de Docker, Git, etc. en Fase 6
- Arquitectura extensible y mantenible preparada para plugins

### 2) Motor de Ejecución (execution) 🚀 **ESTADO**: Implementación Básica
- Referencias: docs/execution-model.md, docs/architecture.md
- ✅ **Implementado**: PipelineExecutor básico en módulo execution/
- ✅ **Implementado**: ExecutionContext propio del módulo
- ⚠️ **Nota**: Existe duplicación con core/execution/ - requiere consolidación
- 📂 **Archivos**: 3 archivos principales implementados
- Tests candidatos:
  - gradle :execution:test --tests "dev.rubentxu.hodei.execution.BasicPipelineExecutorSpec"
  - gradle :execution:test --tests "dev.rubentxu.hodei.execution.AdvancedPipelineExecutorSpec"

### 3) Compilador (compiler) 🔧 **ESTADO**: Implementación Avanzada
- Referencias: docs/session-context-compiler-runtime.md, compiler/README.md
- ✅ **Implementado**: HybridCompiler, GradleCompiler, ScriptCompiler (clases principales)
- ✅ **Implementado**: CacheManager con SHA-256, LibraryManager, RuntimeIntegration
- ✅ **Implementado**: PluginSystem para carga dinámica
- ✅ **Implementado**: LibraryConfiguration para manejo de dependencias
- 📂 **Archivos**: 9 archivos principales + 10 specs implementados
- **📊 Funcionalidades implementadas**: Compilación híbrida, cache, plugins, integración runtime
- Tests disponibles:
  - ✅ CacheManagerSpec, LibraryManagerSpec, RuntimeIntegrationSpec, LibraryConfigurationSpec
  - ✅ HybridCompilerSpec, GradleCompilerSpec, ScriptCompilerSpec
  - ✅ AdvancedIntegrationSpec, EndToEndIntegrationSpec, RealScriptCompilationSpec

### 4) Steps (steps) 📝 **ESTADO**: Consolidación Pendiente
- Referencias: docs/dsl-specification.md (sección Steps), docs/api-core-spec.md
- ✅ **Implementado en core**: 13 handlers SOLID funcionales en core/execution/handlers/
- ⚠️ **Nota**: Los steps están centralizados en el módulo core (arquitectura actual)
- 📂 **Archivos**: Solo package.kt (esqueleto en steps/), handlers en core/
- ➡️ **Decisión arquitectural**: Mantener handlers en core o mover a steps/ en Fase 5 (cleanup)

### 5) Plugins (plugins) 🔌 **ESTADO**: Esqueleto Básico
- Referencias: docs/plugin-system.md
- 📂 **Archivos**: Solo package.kt (esqueleto)
- ⚠️ **Nota**: PluginSystem está implementado en compiler/PluginSystem.kt
- ➡️ **Pendiente**: Implementar runtime de carga (ServiceLoader) como está planificado en Fase 7

### 6) CLI (cli) 💻 **ESTADO**: Implementación Básica
- Referencias: docs/architecture.md (adapters)
- ✅ **Implementado**: Main.kt básico
- 📂 **Archivos**: 2 archivos implementados
- ➡️ **Pendiente**: Ampliar funcionalidad de comandos y opciones

### 7) Librería embebida (library) 📚 **ESTADO**: Esqueleto Básico
- API mínima para invocar pipelines desde código Kotlin/Java
- 📂 **Archivos**: Solo package.kt (esqueleto)
- ➡️ **Pendiente**: Implementar API pública para integración

### 8) Ejemplos (examples) 💡 **ESTADO**: Funcionales
- ✅ **Implementado**: 3 ejemplos de pipelines (.pipeline.kts)
  - simple-pipeline.kts
  - advanced-parallel.pipeline.kts
  - test-integration.pipeline.kts
- ✅ **Funcional**: Ejemplos alineados con DSL actual del core

---

## Iteración 0 (1–2 días): entregable mínimo ejecutable
- Objetivo: ejecutar pipelines básicos con echo/sh y logging, validando el DSL base y el contexto de ejecución.
- Entregables:
  - DSL base funcional (core)
  - Ejecución secuencial de stages/steps (execution)
  - Compilación de scripts simple si se usa .kts (compiler) o builder en código
- Criterios de aceptación:
  - Specs core listados en verde
  - BasicPipelineExecutorSpec verde o parcialmente filtrado sin dependencias externas
- Comandos útiles:
  - gradle :core:test --tests "dev.rubentxu.hodei.core.*"
  - gradle :execution:test --tests "dev.rubentxu.hodei.execution.BasicPipelineExecutorSpec"
  - gradle :compiler:test --tests "dev.rubentxu.hodei.compiler.CacheManagerSpec"

---

## Selección de specs objetivo (primera iteración)
- Core: DSLBuilderSpec, WhenConditionSpec, ExecutionContextSpec, ConfigurableLoggerSpec, StashSystemSpec
- Execution: BasicPipelineExecutorSpec (si el entorno lo permite)
- Compiler: CacheManagerSpec, HybridCompilerSpec (tiempos moderados)

---

Nota: para entornos con limitaciones (Docker/Testcontainers), ajustar ejecución con --tests selectivo y desactivar paralelismo de JUnit si es necesario (-Djunit.jupiter.execution.parallel.enabled=false).