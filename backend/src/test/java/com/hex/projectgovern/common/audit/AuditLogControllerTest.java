package com.hex.projectgovern.common.audit;

import com.hex.projectgovern.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuditLogController 4 case:
 *  - D1 成功(PMO 角色 + 默认 7 天窗口)
 *  - D2 鉴权 403(非 PMO/ADMIN)
 *  - D3 未登录 401
 *  - D4 size 越限(>100 自动截到 100)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "pmo.security.jwt.secret=test-secret-must-be-at-least-64-bytes-long-for-HS512-algorithm-padding!!",
        "pmo.security.jwt.access-expiration-hours=1",
        "pmo.security.jwt.refresh-expiration-days=1"
})
class AuditLogControllerTest {

    @Autowired MockMvc mvc;
    @Autowired OperationLogRepository repository;
    @Autowired JwtService jwtService;
    @Autowired com.hex.projectgovern.module.org.UserRepository userRepository;
    @Autowired com.hex.projectgovern.module.org.RoleRepository roleRepository;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String adminToken;
    private String viewerToken;
    private Long adminUserId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        // 角色 code:V1.4 seed 里有 PMO_ADMIN / VIEWER
        var adminRole = ensureRole("PMO_ADMIN", "PMO管理员");
        var viewerRole = ensureRole("VIEWER", "只读访客");
        // 用 test profile 单独存 role(避免污染其他 test)
        userRepository.deleteAll();
        // 直接走 repo.save(JPA 不会 set id,要 refresh)
        com.hex.projectgovern.module.org.AppUser admin = new com.hex.projectgovern.module.org.AppUser();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("pmo123"));
        admin.setFullName("审计测试 admin");
        admin.setEmail("admin@t.com");
        admin.setPrimaryRole(adminRole);
        admin = userRepository.save(admin);
        com.hex.projectgovern.module.org.AppUser viewer = new com.hex.projectgovern.module.org.AppUser();
        viewer.setUsername("viewer");
        viewer.setPasswordHash(passwordEncoder.encode("pmo123"));
        viewer.setFullName("审计测试 viewer");
        viewer.setEmail("viewer@t.com");
        viewer.setPrimaryRole(viewerRole);
        viewer = userRepository.save(viewer);
        // token 直接绕过 authenticate(用 mock authentication),避开 @SpringBootTest 没走 SecurityFilterChain 的坑
        adminToken = "Bearer " + jwtService.generateAccessToken(admin.getUsername());
        viewerToken = "Bearer " + jwtService.generateAccessToken(viewer.getUsername());
        adminUserId = admin.getId();

        // 灌点数据(7 天内 3 条 + 8 天前 1 条)
        Instant now = Instant.now();
        repository.save(makeLog(adminUserId, "PROJECT", "CREATE", 100L, "{\"result\":\"SUCCESS\"}", now.minus(1, ChronoUnit.HOURS)));
        repository.save(makeLog(adminUserId, "INITIATION", "APPROVE", 200L, "{\"result\":\"SUCCESS\"}", now.minus(2, ChronoUnit.HOURS)));
        repository.save(makeLog(adminUserId, "MILESTONE", "UPDATE_STATUS", 300L, "{\"result\":\"FAILURE\"}", now.minus(3, ChronoUnit.HOURS)));
        repository.save(makeLog(adminUserId, "OLD", "DELETE", 999L, "{\"result\":\"SUCCESS\"}", now.minus(8, ChronoUnit.DAYS)));
    }

    private com.hex.projectgovern.module.org.Role ensureRole(String code, String name) {
        return roleRepository.findAll().stream()
                .filter(r -> code.equals(r.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    var r = new com.hex.projectgovern.module.org.Role();
                    r.setCode(code);
                    r.setName(name);
                    return roleRepository.save(r);
                });
    }

    private com.hex.projectgovern.module.org.AppUser ensureUser(String username, String pw, String fullName,
                                                          String roleCode) {
        return userRepository.findByUsernameAndDeletedFalse(username).orElseGet(() -> {
            var role = roleRepository.findAll().stream()
                    .filter(r -> roleCode.equals(r.getCode()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("role not found: " + roleCode));
            var u = new com.hex.projectgovern.module.org.AppUser();
            u.setUsername(username);
            u.setPasswordHash(passwordEncoder.encode(pw));
            u.setFullName(fullName);
            u.setEmail(username + "@t.com");
            u.setPrimaryRole(role);
            return userRepository.save(u);
        });
    }

    private OperationLog makeLog(Long uid, String mod, String act, Long rid, String payload, Instant t) {
        OperationLog o = new OperationLog();
        o.setUserId(uid);
        o.setResourceType(mod);
        o.setResourceId(rid);
        o.setAction(act);
        o.setPayload(payload);
        o.setIpAddress("127.0.0.1");
        o.setCreatedAt(t);
        return o;
    }

    @Test
    @DisplayName("D1: PMO_ADMIN 角色分页 + 多条件查询 — 默认 7 天窗口(应过滤掉 8 天前的)")
    void listAsPMO() throws Exception {
        mvc.perform(get("/audit-logs").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].resourceType").exists());
    }

    @Test
    @DisplayName("D2: VIEWER 角色 → 403")
    void forbiddenForViewer() throws Exception {
        mvc.perform(get("/audit-logs").header("Authorization", viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D3: 未登录 → 401")
    void unauthenticated() throws Exception {
        mvc.perform(get("/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("D4: size=500 自动截到 100")
    void sizeClamped() throws Exception {
        mvc.perform(get("/audit-logs?size=500").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }
}