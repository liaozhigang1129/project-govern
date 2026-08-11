---
status: draft
created: 2026-08-11
updated: 2026-08-11
summary: 扩展指南 - 新增业务模块 6 步模板
---

# 扩展指南 (Extending)

## 新增业务模块 6 步

以新增 `Cost` 模块为例:

### Step 1: Entity

`backend/src/main/java/com/hex/projectgovern/module/cost/Cost.java`

```java
@Entity
@Table(name = "cost")
@Getter @Setter @NoArgsConstructor
public class Cost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private String currency = "CNY";
    @Column(name = "created_at", nullable = false, updatable = false) 
    private Instant createdAt = Instant.now();
}
```

### Step 2: Repository

`backend/src/main/java/com/hex/projectgovern/module/cost/CostRepository.java`

```java
public interface CostRepository extends JpaRepository<Cost, Long> {
    List<Cost> findByProjectId(Long projectId);
    Optional<Cost> findByProjectIdAndId(Long projectId, Long id);
}
```

### Step 3: DTO (record + @Valid)

`backend/src/main/java/com/hex/projectgovern/module/cost/dto/CostDtos.java`

```java
public class CostDtos {
    public record CreateRequest(
        @NotNull Long projectId,
        @NotNull @DecimalMin("0.00") BigDecimal amount
    ) {}
    public record Response(Long id, Long projectId, BigDecimal amount, String currency) {}
}
```

### Step 4: Service

```java
@Service @RequiredArgsConstructor
public class CostService {
    private final CostRepository repo;
    
    @Transactional
    public Cost create(CostDtos.CreateRequest req) {
        Cost c = new Cost();
        c.setProjectId(req.projectId());
        c.setAmount(req.amount());
        return repo.save(c);
    }
}
```

### Step 5: Controller

```java
@RestController @RequestMapping("/costs")
@RequiredArgsConstructor
public class CostController {
    private final CostService service;
    
    @PostMapping
    public ApiResponse<CostDtos.Response> create(@Valid @RequestBody CostDtos.CreateRequest req) {
        Cost c = service.create(req);
        return ApiResponse.ok(new CostDtos.Response(c.getId(), c.getProjectId(), c.getAmount(), c.getCurrency()));
    }
}
```

### Step 6: Tests

- 单测: `CostServiceTest.java` (mock repo)
- 集成: `CostIntegrationTest.java` (@SpringBootTest + H2)
- 契约: `CostContractTest.java` (MockMvc + JsonPath)

## Flyway 迁移

`backend/src/main/resources/db/migration-mysql/V4.x__cost.sql`:

```sql
CREATE TABLE cost (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cost_project (project_id),
    FOREIGN KEY (project_id) REFERENCES project(id)
);
```

PG 同款 (`migration-pg/`).

## 字典

7 个核心字典 (`module/dict/`):
- `Role` `ProjectType` `ProjectStatus` `HealthLevel`
- `MilestoneStatus` `MilestonePhase` `InitiationStatus`

## 关联事件

- 新增事件 → `common/event/` → publish via `ApplicationEventPublisher`
- 监听: `@EventListener` 或 `@TransactionalEventListener`

## RBAC

Controller 加 `@RequireRoles("PMO_ADMIN")` 限制。