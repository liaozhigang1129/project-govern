package com.company.zhiyu.module.dingtalk;

import com.company.zhiyu.module.org.AppUser;
import com.company.zhiyu.module.org.Department;
import com.company.zhiyu.module.org.DepartmentRepository;
import com.company.zhiyu.module.org.Role;
import com.company.zhiyu.module.org.RoleRepository;
import com.company.zhiyu.module.org.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DingTalkSyncService 用户同步不变量测试
 *
 * 关键承诺: 用户管理同步钉钉, **只改人员信息, 不调整人员角色**。
 * 这里用 Mockito 把钉钉 API 隔离开, 反射调用 protected upsertUser,
 * 验证三条不变量:
 *
 *  1) 已存在用户: 姓名/手机/邮箱/职位/部门/enabled 会被同步覆盖;
 *     primaryRole **不变** (即不调用 setPrimaryRole).
 *  2) 新员工 + autoCreate=true: 分配 default role (字典序最小) 作为初始 primary role。
 *  3) 新员工 + autoCreate=false: 不创建, 直接 skip。
 *
 * 这些测试的目的: 防止后续维护者在 upsertUser 里不小心写了 setPrimaryRole。
 */
@ExtendWith(MockitoExtension.class)
class DingTalkSyncServiceTest {

    @Mock DingTalkProperties props;
    @Mock DingTalkApiClient api;
    @Mock DingTalkSyncLogRepository logRepo;
    @Mock DingTalkUserLookupRepository userLookup;
    @Mock DingTalkDepartmentLookupRepository deptLookup;
    @Mock DingTalkRoleLookupRepository roleLookup;
    @Mock UserRepository userRepo;
    @Mock DepartmentRepository deptRepo;
    @Mock RoleRepository roleRepo;

    DingTalkSyncService service;
    ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new DingTalkSyncService(
                props, api, logRepo,
                userLookup, deptLookup, roleLookup,
                userRepo, deptRepo, roleRepo);
    }

    // ============================================================
    // 1) 已存在用户: 同步覆盖人员信息, 但角色**不变**
    // ============================================================
    @Test
    @DisplayName("upsertUser(已存在): 改姓名/手机/邮箱/职位/部门/enabled, 但不动 primaryRole")
    void upsertUser_existing_keepsRole() throws Exception {
        // given: PMO 已有 id=10 的用户, 主角色是 PMO_ADMIN (roleId=2)
        Role pmoAdmin = new Role(); pmoAdmin.setId(2L); pmoAdmin.setCode("PMO_ADMIN");
        AppUser existing = new AppUser();
        existing.setId(10L);
        existing.setUsername("zhangsan");
        existing.setFullName("原姓名");
        existing.setPhone("11111111111");
        existing.setJobTitle("原职位");
        existing.setDingtalkUserId("dt-001");
        existing.setPrimaryRole(pmoAdmin);
        existing.setEnabled(true);
        existing.setDepartmentId(99L);

        when(userLookup.findByDingtalkUserId("dt-001")).thenReturn(Optional.of(existing));
        when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        // 钉钉返回的"新"信息
        JsonNode u = om.readTree("""
            {"userid":"dt-001","name":"张三(新)","mobile":"22222222222",
             "email":"zhangsan@new.com","title":"架构师","active":true,
             "dept_id_list":[777]}
            """);
        Department newDept = new Department();
        newDept.setId(777L);
        Map<Long, Department> dtToJava = new HashMap<>();
        dtToJava.put(777L, newDept);

        // when
        AppUser saved = invokeUpsertUser(u, dtToJava);

        // then
        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getFullName()).isEqualTo("张三(新)");     // 同步过来
        assertThat(saved.getPhone()).isEqualTo("22222222222");
        assertThat(saved.getEmail()).isEqualTo("zhangsan@new.com");
        assertThat(saved.getJobTitle()).isEqualTo("架构师");
        assertThat(saved.getDepartmentId()).isEqualTo(777L);
        assertThat(saved.isEnabled()).isTrue();
        // 关键断言: primaryRole **没有被动过**
        assertThat(saved.getPrimaryRole()).isSameAs(pmoAdmin);
        assertThat(saved.getPrimaryRole().getCode()).isEqualTo("PMO_ADMIN");
        // 防止后续有人不小心调一次 setPrimaryRole(null) 或 new Role():
        verify(userRepo, times(1)).save(any(AppUser.class));
    }

    @Test
    @DisplayName("upsertUser(已存在, active=false): enabled 同步, 但角色不动")
    void upsertUser_existing_disabled_keepsRole() throws Exception {
        Role pmoAdmin = new Role(); pmoAdmin.setId(2L); pmoAdmin.setCode("PMO_ADMIN");
        AppUser existing = new AppUser();
        existing.setId(11L);
        existing.setUsername("lisi");
        existing.setFullName("原李四");
        existing.setDingtalkUserId("dt-002");
        existing.setPrimaryRole(pmoAdmin);
        existing.setEnabled(true);

        when(userLookup.findByDingtalkUserId("dt-002")).thenReturn(Optional.of(existing));
        when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        JsonNode u = om.readTree("""
            {"userid":"dt-002","name":"李四","mobile":"","email":null,
             "title":"","active":false,"dept_id_list":[]}
            """);

        AppUser saved = invokeUpsertUser(u, new HashMap<>());

        // enabled 跟着钉钉走: true → false
        assertThat(saved.isEnabled()).isFalse();
        // 关键: 角色不动
        assertThat(saved.getPrimaryRole()).isSameAs(pmoAdmin);
        // 关键: 也没有找过 default role
        verify(roleLookup, never()).findDefaultRole();
    }

    // ============================================================
    // 2) 新员工 + autoCreate=true: 给 default role (字典序最小)
    // ============================================================
    @Test
    @DisplayName("upsertUser(新员工, autoCreate=true): 给 default role 作为初始主角色")
    void upsertUser_new_autoCreate_assignsDefaultRole() throws Exception {
        when(props.isAutoCreate()).thenReturn(true);

        when(userLookup.findByDingtalkUserId("dt-new")).thenReturn(Optional.empty());
        when(userLookup.findByPhone("13900000000")).thenReturn(Optional.empty());

        Role guest = new Role(); guest.setId(1L); guest.setCode("GUEST");
        when(roleLookup.findDefaultRole()).thenReturn(Optional.of(guest));

        when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });

        JsonNode u = om.readTree("""
            {"userid":"dt-new","name":"新人","mobile":"13900000000",
             "email":"new@x.com","title":"实习生","active":true,"dept_id_list":[]}
            """);

        AppUser created = invokeUpsertUser(u, new HashMap<>());

        assertThat(created.getId()).isEqualTo(99L);
        assertThat(created.getUsername()).isEqualTo("13900000000");  // 优先 mobile
        assertThat(created.getFullName()).isEqualTo("新人");
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(created.getPrimaryRole()).isSameAs(guest);      // 分配了 default role
        assertThat(created.getPrimaryRole().getCode()).isEqualTo("GUEST");
    }

    @Test
    @DisplayName("upsertUser(新员工, 无 mobile): username 回退到 dt userid")
    void upsertUser_new_noMobile_usernameFallback() throws Exception {
        when(props.isAutoCreate()).thenReturn(true);
        when(userLookup.findByDingtalkUserId("dt-only")).thenReturn(Optional.empty());
        Role guest = new Role(); guest.setId(1L); guest.setCode("GUEST");
        when(roleLookup.findDefaultRole()).thenReturn(Optional.of(guest));
        when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        JsonNode u = om.readTree("""
            {"userid":"dt-only","name":"无名","mobile":"","email":null,
             "title":null,"active":true,"dept_id_list":[]}
            """);

        AppUser created = invokeUpsertUser(u, new HashMap<>());

        assertThat(created.getUsername()).isEqualTo("dt-only");
        assertThat(created.getPrimaryRole()).isSameAs(guest);
    }

    // ============================================================
    // 3) 新员工 + autoCreate=false: 不创建
    // ============================================================
    @Test
    @DisplayName("upsertUser(新员工, autoCreate=false): skip, 不创建, 不查 default role")
    void upsertUser_new_autoCreateFalse_skipped() throws Exception {
        when(props.isAutoCreate()).thenReturn(false);
        when(userLookup.findByDingtalkUserId("dt-skip")).thenReturn(Optional.empty());

        JsonNode u = om.readTree("""
            {"userid":"dt-skip","name":"路过的","mobile":"","email":null,
             "title":null,"active":true,"dept_id_list":[]}
            """);

        AppUser result = invokeUpsertUser(u, new HashMap<>());

        // 应当返回 null (跳过)
        assertThat(result).isNull();
        // 没有 save / 没查 default role
        verify(userRepo, never()).save(any(AppUser.class));
        verify(roleLookup, never()).findDefaultRole();
    }

    // ============================================================
    // helpers
    // ============================================================
    private AppUser invokeUpsertUser(JsonNode u, Map<Long, Department> ctx) throws Exception {
        java.lang.reflect.Method m = DingTalkSyncService.class
                .getDeclaredMethod("upsertUser", JsonNode.class, Map.class);
        m.setAccessible(true);
        try {
            return (AppUser) m.invoke(service, u, ctx);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 解包装业务异常
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw e;
        }
    }
}
