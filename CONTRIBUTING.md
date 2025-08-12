# Contributing to Hodei Pipeline DSL

🎉 Thank you for considering contributing to Hodei Pipeline DSL! This document provides guidelines and information for contributors.

## 🤝 Code of Conduct

By participating in this project, you agree to abide by our Code of Conduct. Please treat all community members with respect and maintain a welcoming environment.

## 🚀 Getting Started

### Prerequisites

- **Java 17+**
- **Kotlin 1.9+**
- **Gradle 8+**
- **Git**

### Setting Up Development Environment

1. **Fork the repository** on GitHub
2. **Clone your fork**:
   ```bash
   git clone https://github.com/YOUR-USERNAME/hodei-dsl.git
   cd hodei-dsl
   ```
3. **Set up upstream remote**:
   ```bash
   git remote add upstream https://github.com/Rubentxu/hodei-dsl.git
   ```
4. **Build the project**:
   ```bash
   gradle build
   ```
5. **Run tests** to ensure everything works:
   ```bash
   gradle test
   ```

## 📋 Development Guidelines

### 🏗️ Architecture Principles

- **SOLID Principles**: Follow Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, and Dependency Inversion
- **Hexagonal Architecture**: Maintain clear separation between domain, application, and infrastructure layers
- **Immutability**: Prefer immutable objects in the domain model
- **Type Safety**: Leverage Kotlin's type system for compile-time safety

### 🎯 TDD Workflow

We follow a strict Test-Driven Development approach:

1. **Red**: Write a failing test for the new feature
2. **Green**: Write the simplest code to make the test pass
3. **Refactor**: Improve the code while ensuring all tests remain green
4. **Commit**: Use conventional commits for changes

### 📝 Coding Standards

#### Kotlin Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions small and focused
- Prefer expression functions when appropriate

#### Comments and Documentation

- Document all public APIs with KDoc
- Use English for all documentation
- Include examples in documentation when helpful
- Avoid obvious comments; focus on explaining "why" not "what"

#### Example:

```kotlin
/**
 * Executes a pipeline step with proper lifecycle management.
 * 
 * This method orchestrates the complete execution lifecycle including
 * validation, preparation, execution, and cleanup phases.
 * 
 * @param step The step to execute
 * @param context The execution context containing environment and configuration
 * @return A [StepResult] containing execution outcome and metadata
 * @throws ValidationException if step validation fails
 */
suspend fun executeWithLifecycle(step: T, context: ExecutionContext): StepResult
```

### 🧪 Testing Guidelines

#### Test Structure

- Use **Kotest** as the testing framework
- Follow **BDD style** with `given-when-then` structure
- Place tests in appropriate modules (`core/src/test/kotlin`, etc.)

#### Test Categories

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test component interactions
- **Handler Tests**: Test step handler implementations
- **DSL Tests**: Test DSL builder functionality

#### Example Test:

```kotlin
class EchoStepHandlerSpec : BehaviorSpec({
    given("an EchoStepHandler") {
        val handler = EchoStepHandler()
        val context = mockk<ExecutionContext>()
        
        `when`("executing a valid echo step") {
            val step = Step.Echo("Hello, World!")
            val result = handler.execute(step, context)
            
            then("should return success result") {
                result.status shouldBe StepStatus.SUCCESS
                result.message shouldBe "Hello, World!"
            }
        }
    }
})
```

### 🔧 Module-Specific Guidelines

#### Core Module

- Maintain domain purity - minimal external dependencies
- All domain objects should be immutable
- Validate business rules in domain objects
- Use factory patterns for complex object creation

#### Execution Module

- Follow handler pattern for step execution
- Implement proper error handling and fault tolerance
- Use coroutines for concurrent execution
- Ensure resource cleanup in all scenarios

#### DSL Module

- Use `@DslMarker` annotations for type safety
- Provide meaningful validation error messages
- Keep builder APIs simple and intuitive
- Support both Kotlin and Java interoperability where applicable

## 🔄 Contribution Process

### 1. Planning

- **Create an Issue**: For new features or bugs, create an issue first
- **Discuss**: Engage in discussion about the approach
- **Get Approval**: Wait for maintainer approval before starting work

### 2. Development

- **Create Branch**: Use descriptive branch names
  ```bash
  git checkout -b feature/add-kubernetes-agent
  git checkout -b fix/step-validation-error
  git checkout -b docs/update-plugin-guide
  ```

- **Implement Changes**: Follow TDD workflow
- **Write Tests**: Ensure comprehensive test coverage
- **Update Documentation**: Update relevant documentation

### 3. Commit Guidelines

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(scope): <description>

[optional body]

[optional footer]
```

#### Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Test changes
- `chore`: Build process or auxiliary tool changes

#### Examples:
```
feat(core): add Kubernetes agent support
fix(execution): resolve step timeout handling
docs(api): update handler registration examples
refactor(dsl): extract common builder patterns
test(handlers): add comprehensive echo handler tests
```

### 4. Pull Request Process

1. **Update Documentation**: Ensure all documentation is current
2. **Run Full Test Suite**: `gradle check`
3. **Create Pull Request**: Use the PR template
4. **Address Feedback**: Respond to review comments promptly
5. **Squash Commits**: Clean up commit history if requested

#### PR Title Format:
```
feat(core): add Kubernetes agent support for containerized execution
```

#### PR Description Template:
```markdown
## Description
Brief description of the changes and their purpose.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review performed
- [ ] Documentation updated
- [ ] Tests pass locally
- [ ] No new warnings introduced
```

## 🐛 Bug Reports

When reporting bugs, please include:

- **Environment**: OS, Java version, Gradle version
- **Steps to Reproduce**: Clear step-by-step instructions
- **Expected Behavior**: What should happen
- **Actual Behavior**: What actually happens
- **Code Sample**: Minimal reproducible example
- **Error Messages**: Complete error messages and stack traces

## 💡 Feature Requests

For feature requests, please provide:

- **Use Case**: Why is this feature needed?
- **Proposed Solution**: How should it work?
- **Alternatives**: Other solutions considered
- **Examples**: Code examples showing desired usage

## 📚 Documentation Contributions

Documentation improvements are always welcome:

- **Fix Typos**: Simple corrections
- **Add Examples**: Practical usage examples
- **Improve Clarity**: Make explanations clearer
- **Add Diagrams**: Mermaid diagrams for complex concepts

## 🏷️ Issue Labels

- `bug`: Something isn't working
- `enhancement`: New feature or request
- `documentation`: Documentation improvements
- `good first issue`: Good for newcomers
- `help wanted`: Extra attention needed
- `question`: Further information requested

## 🎉 Recognition

Contributors will be recognized in:

- **README.md**: Contributor list
- **CHANGELOG.md**: Version release notes
- **GitHub**: Contributor graphs and statistics

Thank you for contributing to Hodei Pipeline DSL! 🚀