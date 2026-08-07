package com.hex.projectgovern.common.testsupport;

import com.hex.projectgovern.module.dict.HealthLevel;
import com.hex.projectgovern.module.dict.HealthLevelRepository;
import com.hex.projectgovern.module.dict.ProjectStatus;
import com.hex.projectgovern.module.dict.ProjectStatusRepository;
import com.hex.projectgovern.module.dict.ProjectType;
import com.hex.projectgovern.module.dict.ProjectTypeRepository;
import com.hex.projectgovern.module.milestone.MilestonePhase;
import com.hex.projectgovern.module.milestone.MilestonePhaseRepository;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.Department;
import com.hex.projectgovern.module.org.DepartmentRepository;
import com.hex.projectgovern.module.org.Role;
import com.hex.projectgovern.module.org.RoleRepository;
import com.hex.projectgovern.module.org.UserRepository;
import com.hex.projectgovern.module.project.Project;
import com.hex.projectgovern.module.project.ProjectRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * ContractTest 共享测试数据初始化器
 *
 * <p>作用:解决 @SpringBootTest 启动时 app_user 表为空的问题,
 *   导致 /auth/login 返回 401,ContractTest 全部失败。
 *
 * <p>使用方式:ContractTest 加 {@code @Import(ContractTestDataInitializer.class)}
 *
 * <p>幂等:每次跑都检查,不存在才创建 (findFirstOrCreate)。
 *   多个 ContractTest 共用同一个 Spring Context,seed 只跑一次。
 *
 * <p>Seed 内容 (与 V1.4__seed_data.sql 主数据保持一致):
 * <ul>
 *   <li>7 个内置角色 (PM / VIEWER / PMO_ADMIN / DEPT_LEAD / EXEC / TEST / DEV)</li>
 *   <li>1 个 PMO 部门</li>
 *   <li>3 个字典 (project_type / project_status / health_level)</li>
 *   <li>7 个里程碑阶段 (V3.1)</li>
 *   <li>6 个常用账号 (admin/pm_zhang/pm_li/lead_wu/vp_chen/viewer)</li>
 *   <li>1 个最小项目 (id=1,供 milestone contract 测试用)</li>
 * </ul>
 */
@TestConfiguration
public class ContractTestDataInitializer {

    // 与 V1.4__seed_data.sql 同款 BCrypt hash (cost=10, of 'pmo123')
    private static final String BCrypt_HASH_pmo123 =
        "$2a$10$j/Vd3KOxoOA0NgbdMa23r.ZN5Ka7sIDJQRsMQCQEwLBkfxdnsLU4G";

    @Bean
    public SeedRunner contractSeedRunner(
        RoleRepository roleRepo,
        UserRepository userRepo,
        DepartmentRepository deptRepo,
        ProjectTypeRepository typeRepo,
        ProjectStatusRepository statusRepo,
        HealthLevelRepository healthRepo,
        MilestonePhaseRepository phaseRepo,
        ProjectRepository projectRepo,
        PasswordEncoder passwordEncoder
    ) {
        return new SeedRunner(roleRepo, userRepo, deptRepo, typeRepo, statusRepo,
            healthRepo, phaseRepo, projectRepo, passwordEncoder);
    }

    /**
     * 实际执行 seed 的类 — 必须在 Spring Context 准备好 JPA + PasswordEncoder 后才能跑,
     * 用 @EventListener(ContextRefreshedEvent) 触发。
     */
    public static class SeedRunner {
        private final RoleRepository roleRepo;
        private final UserRepository userRepo;
        private final DepartmentRepository deptRepo;
        private final ProjectTypeRepository typeRepo;
        private final ProjectStatusRepository statusRepo;
        private final HealthLevelRepository healthRepo;
        private final MilestonePhaseRepository phaseRepo;
        private final ProjectRepository projectRepo;
        private final PasswordEncoder passwordEncoder;

        public SeedRunner(RoleRepository roleRepo, UserRepository userRepo,
                          DepartmentRepository deptRepo,
                          ProjectTypeRepository typeRepo,
                          ProjectStatusRepository statusRepo,
                          HealthLevelRepository healthRepo,
                          MilestonePhaseRepository phaseRepo,
                          ProjectRepository projectRepo,
                          PasswordEncoder passwordEncoder) {
            this.roleRepo = roleRepo;
            this.userRepo = userRepo;
            this.deptRepo = deptRepo;
            this.typeRepo = typeRepo;
            this.statusRepo = statusRepo;
            this.healthRepo = healthRepo;
            this.phaseRepo = phaseRepo;
            this.projectRepo = projectRepo;
            this.passwordEncoder = passwordEncoder;
        }

        @EventListener(ContextRefreshedEvent.class)
        public void seed() {
            // 1. Roles (7 个内置)
            ensureRole("PM",        "项目经理",    10);
            ensureRole("VIEWER",    "只读",        100);
            ensureRole("PMO_ADMIN", "PMO管理员",   5);
            ensureRole("DEPT_LEAD", "部门负责人",  15);
            ensureRole("EXEC",      "高管",        1);
            ensureRole("TEST",      "测试工程师",  30);
            ensureRole("DEV",       "开发工程师",  20);

            // 2. Departments (1 个 PMO)
            if (deptRepo.findAll().stream().noneMatch(d -> "PMO".equals(d.getCode()))) {
                Department pmo = new Department();
                pmo.setCode("PMO");
                pmo.setName("PMO");
                pmo.setSortOrder(0);
                pmo.setEnabled(true);
                deptRepo.save(pmo);
            }

            // 3. Dict (project_type / project_status / health_level)
            if (typeRepo.findAll().isEmpty()) {
                ProjectType t = new ProjectType();
                t.setCode("INTERNAL");
                t.setName("内部项目");
                typeRepo.save(t);
            }
            if (statusRepo.findAll().isEmpty()) {
                ProjectStatus s = new ProjectStatus();
                s.setCode("ACTIVE");
                s.setName("执行中");
                s.setTerminal(false);
                statusRepo.save(s);
            }
            if (healthRepo.findAll().isEmpty()) {
                HealthLevel h = new HealthLevel();
                h.setCode("GREEN");
                h.setName("正常");
                healthRepo.save(h);
            }

            // 4. Milestone phases (7 个)
            if (phaseRepo.count() == 0) {
                phaseRepo.save(newPhase(1L, "INTAKE",    "立项受理", 1, "接收项目意向"));
                phaseRepo.save(newPhase(2L, "ANALYSIS",  "需求分析", 2, "业务/技术需求拆解"));
                phaseRepo.save(newPhase(3L, "PROPOSAL",  "方案撰写", 3, "输出方案/报价"));
                phaseRepo.save(newPhase(4L, "APPROVAL",  "立项审批", 4, "PMO/EXEC 决策"));
                phaseRepo.save(newPhase(5L, "KICKOFF",   "项目启动", 5, "kickoff 会议"));
                phaseRepo.save(newPhase(6L, "EXECUTION", "执行交付", 6, "WBS / 任务执行"));
                phaseRepo.save(newPhase(7L, "CLOSING",   "项目收尾", 7, "验收 / 复盘"));
            }

            // 5. Users (6 个常用账号)
            ensureUser("admin",    "系统管理员", "admin@company.com",    "PMO_ADMIN", "PMO主任");
            ensureUser("pm_zhang", "张三",       "zhang@company.com",    "PM",        "项目经理");
            ensureUser("pm_li",    "李四",       "li@company.com",       "PM",        "项目经理");
            ensureUser("lead_wu",  "吴经理",     "wu@company.com",       "DEPT_LEAD", "研发经理");
            ensureUser("vp_chen",  "陈副总",     "chen@company.com",     "EXEC",      "分管副总");
            ensureUser("viewer",   "只读访客",   "viewer@company.com",   "VIEWER",    "审计员");

            // 6. Project id=1 (供 milestone contract 测试的 projectId=1 引用)
            if (projectRepo.findById(1L).isEmpty()) {
                Project p = new Project();
                p.setCode("P-CONTRACT-TEST");
                p.setName("契约测试项目");
                p.setCustomer("内部");
                p.setDescription("contract test 专用最小项目");
                p.setType(typeRepo.findAll().get(0));
                p.setStatus(statusRepo.findAll().get(0));
                p.setHealth(healthRepo.findAll().get(0));
                p.setDepartmentId(1L);
                // pm_user_id = pm_zhang.id
                userRepo.findByUsernameAndDeletedFalse("pm_zhang")
                    .ifPresent(u -> p.setPmUserId(u.getId()));
                projectRepo.save(p);
            }
        }

        // ============ helpers ============

        private void ensureRole(String code, String name, int sortOrder) {
            if (roleRepo.findAll().stream().noneMatch(r -> code.equals(r.getCode()))) {
                Role r = new Role();
                r.setCode(code);
                r.setName(name);
                r.setBuiltIn(true);
                r.setEnabled(true);
                r.setSortOrder(sortOrder);
                roleRepo.save(r);
            }
        }

        private void ensureUser(String username, String fullName, String email,
                                String roleCode, String jobTitle) {
            if (userRepo.existsByUsername(username)) return;
            Role role = roleRepo.findAll().stream()
                .filter(r -> roleCode.equals(r.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("role not found: " + roleCode));
            Department dept = deptRepo.findAll().stream()
                .filter(d -> "PMO".equals(d.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("department PMO not found"));

            AppUser u = new AppUser();
            u.setUsername(username);
            u.setPasswordHash(passwordEncoder.encode("pmo123")); // 运行时 hash,确保跟配置一致
            u.setFullName(fullName);
            u.setEmail(email);
            u.setPrimaryRole(role);
            u.setDepartmentId(dept.getId());
            u.setJobTitle(jobTitle);
            u.setEnabled(true);
            u.setDeleted(false);
            u.setLoginFailCount(0);
            u.setMustChangePassword(false);
            u.setDefaultHourlyRate(java.math.BigDecimal.ZERO);
            userRepo.save(u);
        }

        private MilestonePhase newPhase(Long id, String code, String name, int sortOrder, String desc) {
            MilestonePhase p = new MilestonePhase();
            p.setId(id);
            p.setCode(code);
            p.setName(name);
            p.setSortOrder(sortOrder);
            p.setDescription(desc);
            return p;
        }
    }
}