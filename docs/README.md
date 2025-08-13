# Documentación Técnica - Hodei Pipeline DSL

## Índice de Documentación

Esta documentación está organizada por módulos y fases de implementación:

### 📋 Especificaciones Técnicas
- [**Arquitectura General**](./architecture.md) - Diseño del sistema completo
- [**API Core**](./api-core-spec.md) - Especificaciones de la API principal
- [**DSL Syntax**](./dsl-specification.md) - Sintaxis y semántica del DSL
- [**Sistema de Plugins**](./plugin-system.md) - Arquitectura de plugins dinámicos
- [**Modelo de Ejecución**](./execution-model.md) - Concurrencia y paralelismo

### 🚀 Guías de Implementación
- [**Plan de Desarrollo**](./development-plan.md) - Cronograma y fases
- [**Configuración del Proyecto**](./project-setup.md) - Estructura y dependencias
- [**Testing Strategy**](./testing-strategy.md) - Estrategia de pruebas

### 🔧 APIs y Referencias
- [**Core API Reference**](./api-reference.md) - Documentación de APIs
- [**Jenkins Steps Compatibility**](./jenkins-compatibility.md) - Mapeo de steps de Jenkins
- [**Plugin Development Guide**](./plugin-development.md) - Guía para desarrollar plugins

### 💡 Ejemplos y Casos de Uso
- [**Ejemplos Básicos**](./examples/basic.md) - Pipelines simples y introductorios
- [**Ejemplos Avanzados**](./examples/advanced.md) - Casos empresariales complejos
- [**Integración**](./examples/integration.md) - Migración desde Jenkins, GitLab CI, GitHub Actions

### 📊 Diagramas y Visualizaciones
- [**Diagramas de Arquitectura**](./diagrams/) - Diagramas técnicos
- [**Flujos de Ejecución**](./flows/) - Diagramas de secuencia y flujo

### 🕑 Contexto de Sesión
- [**Compiler & Runtime – Contexto de Sesión**](./session-context-compiler-runtime.md)
  - Mostrar por CLI: `gradle :cli:run --args "context"` (desde la raíz del repo)
  - Ruta por defecto: `docs/session-context-compiler-runtime.md` (configurable con `--path`)

## Convenciones de Documentación

- **Idiomas**: Toda la documentación técnica está en español e inglés
- **Formato**: Markdown con extensiones de GitHub
- **Ejemplos**: Código Kotlin con sintaxis highlighting
- **Diagramas**: Mermaid para diagramas técnicos

## Estado del Proyecto

### Fase Actual: Core Development + SOLID Architecture ✅
- [x] Estructura de documentación completa
- [x] Arquitectura general implementada y documentada
- [x] Especificaciones de APIs finalizadas
- [x] Ejemplos de uso documentados
- [x] **Sistema SOLID de Handlers implementado**
- [x] **Core DSL funcional y completamente testado**

### ✅ **Hitos Técnicos Completados**
- **SOLID Refactoring FASE 1**: Infrastructure (StepHandler, Registry, AbstractStepHandler) ✅
- **SOLID Refactoring FASE 2**: Handlers simples (Echo, Shell, ArchiveArtifacts, PublishTestResults, Stash, Unstash) ✅
- **Test Coverage**: 100% success rate en todos los handlers implementados
- **Documentación**: Arquitectura, execution model y API specs actualizadas

### Próximas Fases
1. **SOLID Refactoring FASE 3-5** - Handlers complejos, modernización DSL, cleanup legacy
2. **Motor de Ejecución Avanzado** - Features adicionales y optimizaciones
3. **Compilador de Scripts** - Engine de Kotlin Scripts
4. **Sistema de Plugins** - Extensibilidad dinámica

---

**Versión**: 1.0.0-SNAPSHOT  
**Última actualización**: 2025-01-12  
**Licencia**: MIT