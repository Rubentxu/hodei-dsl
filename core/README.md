# Core Module

The core module contains the domain models, interfaces, and business logic of the Hodei Pipeline DSL system.

## Architecture

This module follows hexagonal architecture principles:

- **Domain Models**: Pure domain objects (`Pipeline`, `Stage`, `Step`)
- **Port Interfaces**: Contracts that adapters must implement
- **Business Rules**: Core validation and processing logic
- **Type-safe DSL**: Kotlin DSL builders with compile-time safety

## Key Components

- `dev.rubentxu.hodei.core.domain` - Domain models
- `dev.rubentxu.hodei.core.ports` - Port interfaces for adapters
- `dev.rubentxu.hodei.core.dsl` - DSL builders and markers
- `dev.rubentxu.hodei.core.validation` - Pipeline validation logic

## Dependencies

This module has minimal dependencies to maintain purity:
- Kotlin stdlib and coroutines
- Kotlinx serialization for JSON support