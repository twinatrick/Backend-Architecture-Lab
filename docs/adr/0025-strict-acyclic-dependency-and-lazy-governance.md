# 25. Strict Acyclic Dependency Graph (DAG), Complete `@Lazy` Prohibition, and Explicit Aspect Executors

Date: 2026-10-29

## Status

Accepted

## Context

During the refactoring of `backend-iam-service` to synchronize L2 Redis cache eviction with L1 Caffeine near-cache eviction, a bidirectional dependency emerged when `UserService`, `RoleService`, and `FunctionCommandService` attempted to inject `LocalPermissionValidator`, while `LocalPermissionValidatorImpl` already depended on `IUserService` and `IFunctionQueryService`.

Using Spring's `@Lazy` annotation on constructor dependencies or enabling `spring.main.allow-circular-references=true` masks the underlying architectural smell (Circular Dependency Masking) rather than resolving entangled responsibilities. Furthermore, even self-proxy injection (`@Lazy private final I<Self>Service self;`)—historically used to trigger `@Cacheable`, `@CacheEvict`, or `@Transactional(propagation = Propagation.REQUIRES_NEW)` within the same class—creates a self-referential bean cycle, complicates unit test construction, and encourages developers to treat `@Lazy` as an acceptable workaround. Conversely, removing `self` and calling `this.method()` directly silently bypasses Spring AOP proxies altogether.

## Decision

We establish a project-wide **Strict Acyclic Dependency Graph (4-Tier DAG)** and **Explicit Aspect Executor** standard, enforced via documentation and automated pre-commit static gates:

1. **Four-Tier Acyclic Dependency Standard (4-Tier DAG)**:
   - **Tier 1 (Inter-Service)**: Business microservices (`backend-competency-service`, `backend-job-service`, `backend-alert-service`, `backend-external-api-service`) $\rightarrow$ `backend-iam-service` $\rightarrow$ `backend-common`. Reverse calls or mutual Feign dependencies are strictly forbidden.
   - **Tier 2 (Inter-Layer)**: `Controller` / `Filter` / `WebSocket` / `Timer` $\rightarrow$ `Service` $\rightarrow$ `DataAccess` $\rightarrow$ `Repository` $\rightarrow$ `Entity`. Supporting components (`Mapper`, `Util`) must remain strictly downstream and never inject `Service` or `Controller` beans.
   - **Tier 3 (Intra-Layer Cross-Bean)**: Services and cross-cutting components (`Aop`, `Security`, `Service`) must never form direct ($A \leftrightarrow B$), indirect ($A \rightarrow B \rightarrow C \rightarrow A$), or self-referential ($A \rightarrow A$) dependency cycles. When two components need to share state or coordinate:
     - **Dependency Demotion (Lower-Level Extraction)**: Extract shared state/cache/logic into a zero-business-dependency lower-level component (e.g., `com.example.BackendArchitectureLab.Util.IamLocalCacheManager`).
     - **Orchestrator Extraction**: Introduce a higher-level facade/command service that depends unidirectionally on both services.
     - **Domain Event Decoupling**: Publish Spring `ApplicationEvent` / `@TransactionalEventListener` for post-commit side effects.
   - **Tier 4 (JPA Entity & Python Module)**: Join/child JPA entities (`@ManyToOne`) must never declare `cascade = CascadeType.ALL` or `CascadeType.REMOVE` toward parent entities; Python modules must never form circular `import` graphs.

2. **Complete Prohibition of `@Lazy` and Explicit Aspect Executors**:
   - Annotating `@Lazy` on **any** field, constructor parameter, or method in production code (including `@Lazy private final I<Self>Service self;`) is strictly prohibited.
   - Enabling `spring.main.allow-circular-references=true` or calling `setAllowCircularReferences(true)` is strictly prohibited.
   - When a service needs to trigger transactional boundaries or cache operations internally without self-invocation proxy bypass, it must delegate unidirectionally to **Explicit Aspect Executors** in `backend-common`:
     - `com.example.BackendArchitectureLab.Util.TransactionExecutor` (`executeReadOnly`, `runReadOnly`, `executeWritable`, `runWritable`, `executeRequiresNew`, `runRequiresNew`).
     - `com.example.BackendArchitectureLab.Util.CacheExecutor` (`executeCacheable`, `executeCacheableWrapper`, `executeCacheableList`, `put`, `evict`).

3. **Automated Pre-commit Static Gate Enforcement (`.github/ai-review/`)**:
   - `RULE_PROHIBIT_CROSS_BEAN_LAZY` (`HIGH`): Blocks any `@Lazy` annotation in production Java code (including `self` injection).
   - `RULE_SELF_INVOCATION_BYPASS` (`HIGH`): Blocks internal `this.method()` self-invocation targeting methods annotated with `@Transactional`, `@Cacheable`, `@CacheEvict`, `@CachePut`, or `@Caching`.
   - `RULE_CIRCULAR_DEPENDENCY` (`HIGH`): Performs Depth-First Search (DFS) cycle detection across Java component dependencies within the same microservice and across Python module imports.
   - `RULE_SPRING_CIRCULAR_REF_CONFIG` (`HIGH`): Blocks `allow-circular-references: true` in configuration files or Java code.
   - `RULE_JPA_CHILD_CASCADE` (`HIGH`): Blocks `@ManyToOne` annotations configured with `CascadeType.ALL` or `CascadeType.REMOVE`.

## Consequences

- **Positive**: Zero `@Lazy` annotations in production code; eliminates both cross-bean and self-referential dependency cycles; prevents silent Spring AOP proxy bypass on internal cache/transaction calls; simplifies unit testing by injecting lightweight `TransactionExecutor` and `CacheExecutor`.
- **Negative**: Internal cached or transactional helper calls must be invoked via `cacheExecutor` / `transactionExecutor` lambdas rather than calling `@Cacheable` / `@Transactional` methods on `this` or `self`.
