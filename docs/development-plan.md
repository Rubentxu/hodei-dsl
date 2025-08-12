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
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 2

#### Tareas Principales (TDD/BDD)
- [ ] **RED**: Escribir PipelineSpec con comportamientos esperados
- [ ] **GREEN**: Implementar interfaces base del Pipeline DSL
- [ ] **RED**: Escribir StageBuilderSpec para type-safe builders
- [ ] **GREEN**: Crear builders type-safe para Pipeline, Stage, Steps
- [ ] **RED**: Escribir ExecutionContextSpec para manejo de contextos
- [ ] **GREEN**: Implementar sistema de contexto de ejecución
- [ ] **REFACTOR**: Aplicar principios SOLID y clean architecture
- [ ] **COMMIT**: `feat(core): implement pipeline DSL foundation`

#### Criterios de Aceptación
- DSL funcional con type-safety completa
- API intuitiva y compatible con Jenkins
- Validación de sintaxis implementada
- Cobertura de tests > 90%

#### Entregables
- Módulo `core` completo y testado
- API pública estable
- Documentación de API actualizada

---

### **Fase 4: Compilador de Scripts** 🔧
**Duración**: 3-4 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 3

#### Tareas Principales (TDD/BDD)
- [ ] **RED**: Escribir KotlinScriptCompilerSpec para compilación básica
- [ ] **GREEN**: Integrar Kotlin Script Engine
- [ ] **RED**: Escribir CacheSpec para optimización de compilación
- [ ] **GREEN**: Implementar sistema de cache de compilación
- [ ] **RED**: Escribir ErrorHandlingSpec para diagnósticos
- [ ] **GREEN**: Implementar manejo robusto de errores
- [ ] **REFACTOR**: Optimizar performance y clean code
- [ ] **COMMIT**: `feat(compiler): add kotlin script compilation support`

#### Criterios de Aceptación
- Compilación rápida de scripts `.kts`
- Manejo robusto de errores
- Cache eficiente implementado
- Soporte para @DependsOn y @Repository

#### Entregables
- Módulo `compiler` funcional
- Sistema de cache optimizado
- Diagnósticos de error mejorados

---

### **Fase 5: Motor de Ejecución** 🚀
**Duración**: 4-5 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 4

#### Tareas Principales
- [ ] Implementar executor basado en coroutines
- [ ] Soporte para ejecución paralela real
- [ ] Sistema de timeouts y retry
- [ ] Manejo de contextos (Docker, Kubernetes)
- [ ] Event bus para comunicación entre stages
- [ ] Structured concurrency implementation

#### Criterios de Aceptación
- Ejecución paralela eficiente
- Manejo robusto de timeouts
- Soporte para múltiples contextos de ejecución
- Structured concurrency implementada

#### Entregables
- Módulo `executor` completo
- Soporte para contenedores
- Sistema de eventos implementado

---

### **Fase 6: Steps de Jenkins** 📝
**Duración**: 3-4 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 5

#### Tareas Principales
- [ ] Implementar steps básicos (`sh`, `echo`, `dir`, `withEnv`)
- [ ] Steps de archiving (`archiveArtifacts`, `junit`)
- [ ] Steps de Docker (`docker.image`, `docker.withRegistry`)
- [ ] Steps de control de flujo (`parallel`, `retry`, `timeout`)
- [ ] Steps de Git y SCM
- [ ] Compatibilidad completa con sintaxis Jenkins

#### Criterios de Aceptación
- Todos los steps principales de Jenkins implementados
- Compatibilidad de sintaxis 100%
- Comportamiento idéntico a Jenkins
- Tests de compatibilidad exitosos

#### Entregables
- Librería completa de steps
- Tests de compatibilidad
- Documentación de steps

---

### **Fase 7: Sistema de Plugins** 🔌
**Duración**: 4-5 días  
**Estado**: ⏳ Pendiente  
**Dependencias**: Fase 6

#### Tareas Principales
- [ ] API de plugins dinámicos
- [ ] ClassLoader isolation para plugins
- [ ] Generación automática de código DSL
- [ ] Plugin registry y discovery
- [ ] Sistema de dependencias de plugins
- [ ] Sandboxing de seguridad

#### Criterios de Aceptación
- Plugins cargables dinámicamente
- Aislamiento de seguridad implementado
- Generación de DSL automática
- Registry de plugins funcional

#### Entregables
- Framework de plugins completo
- Ejemplos de plugins
- Guía de desarrollo de plugins

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

## Estimaciones Totales

| Fase | Duración | Tipo |
|------|----------|------|
| **Fase 1-2** | 3-4 días | Preparación |
| **Fase 3-5** | 10-13 días | Core Development |
| **Fase 6-7** | 7-9 días | Features & Extensions |
| **Fase 8-9** | 4-6 días | Interfaces |
| **Fase 10-11** | 5-7 días | Finalización |
| **TOTAL** | **29-39 días** | **Completo** |

## Hitos Importantes

- **🎯 Hito 1** (Final Fase 3): Core DSL funcional
- **🎯 Hito 2** (Final Fase 5): Motor de ejecución completo
- **🎯 Hito 3** (Final Fase 7): Sistema de plugins operativo
- **🎯 Hito 4** (Final Fase 9): APIs públicas finalizadas
- **🎯 Hito 5** (Final Fase 11): Release 1.0 lista

## Riesgos y Mitigaciones

### Riesgos Técnicos
- **Complejidad del Kotlin Script Engine**: Mitigación con POCs tempranos
- **Performance de ejecución paralela**: Benchmarking continuo
- **Compatibilidad con Jenkins**: Tests de compatibilidad extensivos

### Riesgos de Cronograma
- **Estimaciones optimistas**: Buffer de 20% en cada fase
- **Dependencias entre fases**: Paralelización donde sea posible
- **Complejidad no prevista**: Reviews técnicas frecuentes

---

**Versión del Plan**: 1.0  
**Fecha de Creación**: $(date +'%Y-%m-%d')  
**Próxima Revisión**: Semanal

## Backlog Inicial por Módulo (alineado con /docs)

Esta sección deriva directamente de las especificaciones en /docs y prioriza un arranque incremental. Cada ítem referencia su documento base cuando aplica.

### 1) Núcleo (core)
- Referencias: docs/api-core-spec.md, docs/dsl-specification.md, docs/architecture.md
- MVP DSL validado: pipeline, agent(any/label), environment(set/credentials), stages, steps (echo, sh), post(always)
- ExecutionContext: variables, workspace, logger configurable, stash básico
- WhenCondition: all/any/not + DSL (ya modelado)
- Builders: PipelineBuilder, StageBuilder, StepsBuilder coherentes con la spec
- Tests a ejecutar (iteración 0):
  - gradle :core:test --tests "dev.rubentxu.hodei.core.dsl.DSLBuilderSpec"
  - gradle :core:test --tests "dev.rubentxu.hodei.core.domain.WhenConditionSpec"
  - gradle :core:test --tests "dev.rubentxu.hodei.core.domain.WhenConditionDSLSpec"
  - gradle :core:test --tests "dev.rubentxu.hodei.core.execution.ExecutionContextSpec"
  - gradle :core:test --tests "dev.rubentxu.hodei.core.execution.ConfigurableLoggerSpec"
  - gradle :core:test --tests "dev.rubentxu.hodei.core.execution.StashSystemSpec"
- Criterios: specs anteriores en verde, sin API pública inestable adicional.

### 2) Motor de Ejecución (execution)
- Referencias: docs/execution-model.md, docs/architecture.md
- Structured concurrency: PipelineExecutor/StageExecutor/StepExecutor coherentes con jerarquía de scopes
- Dispatchers por WorkloadType y timeouts/retry básicos del FaultToleranceConfig
- Logging de eventos de pipeline y métricas mínimas
- Tests candidatos:
  - gradle :execution:test --tests "dev.rubentxu.hodei.execution.BasicPipelineExecutorSpec"
  - gradle :execution:test --tests "dev.rubentxu.hodei.execution.AdvancedPipelineExecutorSpec" (evitar en entornos limitados)

### 3) Compilador (compiler)
- Referencias: docs/session-context-compiler-runtime.md, compiler/README.md
- Verificar integración HybridCompiler/GradleCompiler/ScriptCompiler conforme a docs
- CacheManager operativo y validado con SHA-256; LibraryManager estable
- Tests candidatos:
  - gradle :compiler:test --tests "dev.rubentxu.hodei.compiler.CacheManagerSpec"
  - gradle :compiler:test --tests "dev.rubentxu.hodei.compiler.LibraryManagerSpec"
  - gradle :compiler:test --tests "dev.rubentxu.hodei.compiler.RuntimeIntegrationSpec"
  - gradle :compiler:test --tests "dev.rubentxu.hodei.compiler.HybridCompilerSpec"

### 4) Steps (steps)
- Referencias: docs/dsl-specification.md (sección Steps), docs/api-core-spec.md
- Prioridad: echo, sh, dir, withEnv; registro y resolución de steps
- Compatibilidad de parámetros con Jenkins donde aplique

### 5) Plugins (plugins)
- Referencias: docs/plugin-system.md
- Esqueleto: contratos de plugin, registry/discovery básico, isolation mínima
- No se requiere generación dinámica aún (dejar para fase posterior)

### 6) CLI (cli)
- Referencias: docs/architecture.md (adapters)
- Comando básico: hodei run <archivo.pipeline.kts>
- Logging legible y códigos de salida; lectura de variables de entorno

### 7) Librería embebida (library)
- API mínima para invocar pipelines desde código Kotlin/Java
- Alinear paquetes públicos con explicitApi()

### 8) Ejemplos (examples)
- Validar examples/simple-pipeline.kts con core/execution actuales
- Agregar ejemplo de paralelismo reducido si es viable sin Docker

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