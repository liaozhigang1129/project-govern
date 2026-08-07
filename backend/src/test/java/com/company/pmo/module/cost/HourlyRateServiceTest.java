package com.company.pmo.module.cost;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.cost.dto.HourlyRateUpsertRequest;
import com.company.pmo.module.cost.dto.RoleCostDefaultUpdateRequest;
import com.company.pmo.module.org.AppUser;
import com.company.pmo.module.org.Role;
import com.company.pmo.module.org.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HourlyRateService 单元测试 — P0-A.1 F1 工时→成本引擎
 *
 * <p>覆盖:
 * <ul>
 *   <li>resolveRate 4 级兜底链 (USER_OVERRIDE → ROLE_OVERRIDE → ROLE_COST_DEFAULT → USER_DEFAULT → NONE)</li>
 *   <li>CRUD: create / update / close / delete (含重叠检测、已生效不能 delete)</li>
 *   <li>CSV import: 正常行 + 坏行收集</li>
 * </ul>
 *
 * <p>纯 Mockito, 不连数据库。
 */
@ExtendWith(MockitoExtension.class)
class HourlyRateServiceTest {

    @Mock HourlyRateRepository hourlyRateRepo;
    @Mock RoleCostDefaultRepository roleDefaultRepo;
    @Mock UserRepository userRepository;

    @InjectMocks HourlyRateService service;

    private AppUser user;
    private Role devRole;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setId(1L);
        user.setUsername("zhangsan");
        user.setFullName("张三");
        user.setDefaultHourlyRate(new BigDecimal("500.00"));

        devRole = new Role();
        devRole.setId(10L);
        devRole.setCode("DEV");
        devRole.setName("开发工程师");
        user.setPrimaryRole(devRole);
    }

    // ============================================================
    // resolveRate 4 级兜底链
    // ============================================================
    @Nested
    @DisplayName("resolveRate — 4 级兜底链")
    class ResolveRateChain {

        @Test
        @DisplayName("L1 USER_OVERRIDE: user_id 命中 → 600")
        void userOverride() {
            LocalDate month = YearMonth.of(2026, 6).atDay(1);
            HourlyRate hr = new HourlyRate();
            hr.setRoleCode("DEV");
            hr.setUser(user);
            hr.setRate(new BigDecimal("600.00"));
            when(hourlyRateRepo.findActiveUserRate(1L, month))
                    .thenReturn(List.of(hr));

            var res = service.resolveRate(1L, "DEV", month);
            assertThat(res.rate()).isEqualByComparingTo("600");
            assertThat(res.source()).isEqualTo(HourlyRateService.RateSource.USER_OVERRIDE);
            verify(hourlyRateRepo, never()).findActiveRoleRate(any(), any());
        }

        @Test
        @DisplayName("L2 ROLE_OVERRIDE: user 命中失败 → role_id 命中 → 550")
        void roleOverride() {
            LocalDate month = YearMonth.of(2026, 6).atDay(1);
            HourlyRate hr = new HourlyRate();
            hr.setRoleCode("DEV");
            hr.setUser(null);
            hr.setRate(new BigDecimal("550.00"));
            when(hourlyRateRepo.findActiveUserRate(1L, month))
                    .thenReturn(List.of());
            when(hourlyRateRepo.findActiveRoleRate("DEV", month))
                    .thenReturn(List.of(hr));

            var res = service.resolveRate(1L, "DEV", month);
            assertThat(res.rate()).isEqualByComparingTo("550");
            assertThat(res.source()).isEqualTo(HourlyRateService.RateSource.ROLE_OVERRIDE);
            verify(roleDefaultRepo, never()).findById(any());
        }

        @Test
        @DisplayName("L3 ROLE_COST_DEFAULT: role_id 也无 → role_cost_default 字典 → 450")
        void roleCostDefault() {
            LocalDate month = YearMonth.of(2026, 6).atDay(1);
            when(hourlyRateRepo.findActiveUserRate(1L, month)).thenReturn(List.of());
            when(hourlyRateRepo.findActiveRoleRate("DEV", month)).thenReturn(List.of());
            RoleCostDefault def = new RoleCostDefault();
            def.setCode("DEV");
            def.setRate(new BigDecimal("450.00"));
            when(roleDefaultRepo.findById("DEV")).thenReturn(Optional.of(def));

            var res = service.resolveRate(1L, "DEV", month);
            assertThat(res.rate()).isEqualByComparingTo("450");
            assertThat(res.source()).isEqualTo(HourlyRateService.RateSource.ROLE_COST_DEFAULT);
        }

        @Test
        @DisplayName("L4 USER_DEFAULT: 前 3 级都无 → app_user.default_hourly_rate → 500")
        void userDefault() {
            LocalDate month = YearMonth.of(2026, 6).atDay(1);
            when(hourlyRateRepo.findActiveUserRate(1L, month)).thenReturn(List.of());
            when(hourlyRateRepo.findActiveRoleRate("DEV", month)).thenReturn(List.of());
            when(roleDefaultRepo.findById("DEV")).thenReturn(Optional.empty());
            when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));

            var res = service.resolveRate(1L, "DEV", month);
            assertThat(res.rate()).isEqualByComparingTo("500");
            assertThat(res.source()).isEqualTo(HourlyRateService.RateSource.USER_DEFAULT);
        }

        @Test
        @DisplayName("L5 NONE: 全部兜底失败 → 0")
        void noneFallback() {
            LocalDate month = YearMonth.of(2026, 6).atDay(1);
            when(hourlyRateRepo.findActiveUserRate(1L, month)).thenReturn(List.of());
            when(hourlyRateRepo.findActiveRoleRate("DEV", month)).thenReturn(List.of());
            when(roleDefaultRepo.findById("DEV")).thenReturn(Optional.empty());
            user.setDefaultHourlyRate(BigDecimal.ZERO); // 用户默认也是 0
            when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));

            var res = service.resolveRate(1L, "DEV", month);
            assertThat(res.rate()).isEqualByComparingTo("0");
            assertThat(res.source()).isEqualTo(HourlyRateService.RateSource.NONE);
        }

        @Test
        @DisplayName("userId=null 时跳过 L1/L4, 只能走 L2/L3/L5")
        void userIdNull_skipsUserLevel() {
            LocalDate month = YearMonth.of(2026, 6).atDay(1);
            when(hourlyRateRepo.findActiveRoleRate("DEV", month)).thenReturn(List.of());
            RoleCostDefault def = new RoleCostDefault();
            def.setRate(new BigDecimal("450.00"));
            when(roleDefaultRepo.findById("DEV")).thenReturn(Optional.of(def));

            var res = service.resolveRate(null, "DEV", month);
            assertThat(res.source()).isEqualTo(HourlyRateService.RateSource.ROLE_COST_DEFAULT);
            verify(hourlyRateRepo, never()).findActiveUserRate(anyLong(), any());
        }

        @Test
        @DisplayName("roleCode=null/blank 时跳过 L2/L3, 直接到 L4/L5")
        void roleCodeBlank_skipsRoleLevel() {
            LocalDate month = YearMonth.of(2026, 6).atDay(1);
            when(hourlyRateRepo.findActiveUserRate(1L, month)).thenReturn(List.of());
            when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));

            var res = service.resolveRate(1L, "", month);
            assertThat(res.source()).isEqualTo(HourlyRateService.RateSource.USER_DEFAULT);
            verify(hourlyRateRepo, never()).findActiveRoleRate(any(), any());
        }
    }

    // ============================================================
    // CRUD
    // ============================================================
    @Nested
    @DisplayName("HourlyRate CRUD")
    class Crud {

        @Test
        @DisplayName("create: 角色档 (userId=null) 走 validate → repo.save → return DTO")
        void create_roleDefault_ok() {
            HourlyRateUpsertRequest req = new HourlyRateUpsertRequest(
                    "DEV", null, new BigDecimal("450.00"),
                    YearMonth.of(2026, 6), null, "种子价");
            when(hourlyRateRepo.findOverlap(eq(null), eq("DEV"), any(), any(), eq(null)))
                    .thenReturn(List.of());
            when(hourlyRateRepo.save(any())).thenAnswer(inv -> {
                HourlyRate h = inv.getArgument(0);
                h.setId(99L);
                return h;
            });

            var item = service.create(req, 1L);
            assertThat(item.id()).isEqualTo(99L);
            assertThat(item.roleCode()).isEqualTo("DEV");
            assertThat(item.userId()).isNull();
            assertThat(item.rate()).isEqualByComparingTo("450");
            verify(hourlyRateRepo).save(any());
        }

        @Test
        @DisplayName("create: userId 不存在 → 400")
        void create_userNotFound_400() {
            HourlyRateUpsertRequest req = new HourlyRateUpsertRequest(
                    "DEV", 999L, new BigDecimal("600.00"),
                    YearMonth.of(2026, 6), null, null);
            when(userRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("userId 不存在");
            verify(hourlyRateRepo, never()).save(any());
        }

        @Test
        @DisplayName("create: 重叠区间 → 400")
        void create_overlap_400() {
            HourlyRateUpsertRequest req = new HourlyRateUpsertRequest(
                    "DEV", null, new BigDecimal("450.00"),
                    YearMonth.of(2026, 6), null, null);
            HourlyRate existing = new HourlyRate();
            existing.setId(50L);
            when(hourlyRateRepo.findOverlap(eq(null), eq("DEV"), any(), any(), eq(null)))
                    .thenReturn(List.of(existing));

            assertThatThrownBy(() -> service.create(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("费率区间冲突");
        }

        @Test
        @DisplayName("create: rate=0 → IllegalArgumentException")
        void create_invalidRate() {
            HourlyRateUpsertRequest req = new HourlyRateUpsertRequest(
                    "DEV", null, BigDecimal.ZERO,
                    YearMonth.of(2026, 6), null, null);
            assertThatThrownBy(() -> service.create(req, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("close: 设置 endMonth = atMonth.end")
        void close_setsEndMonth() {
            HourlyRate h = new HourlyRate();
            h.setId(7L);
            h.setRoleCode("DEV");
            h.setEffectiveMonth(YearMonth.of(2026, 1).atDay(1));
            when(hourlyRateRepo.findByIdWithUser(7L)).thenReturn(Optional.of(h));
            when(hourlyRateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var item = service.close(7L, YearMonth.of(2026, 12), 1L);
            assertThat(item.endMonth()).isEqualTo(YearMonth.of(2026, 12));
        }

        @Test
        @DisplayName("close: 关停月份早于生效月份 → 400")
        void close_invalidDate() {
            HourlyRate h = new HourlyRate();
            h.setId(7L);
            h.setEffectiveMonth(YearMonth.of(2026, 6).atDay(1));
            when(hourlyRateRepo.findByIdWithUser(7L)).thenReturn(Optional.of(h));

            assertThatThrownBy(() -> service.close(7L, YearMonth.of(2026, 1), 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("关停月份");
        }

        @Test
        @DisplayName("delete: effectiveMonth 已 ≤ 本月 → 400 (需用 close)")
        void delete_alreadyEffective_blocked() {
            HourlyRate h = new HourlyRate();
            h.setId(7L);
            h.setEffectiveMonth(LocalDate.now().withDayOfMonth(1)); // 本月
            when(hourlyRateRepo.findByIdWithUser(7L)).thenReturn(Optional.of(h));

            assertThatThrownBy(() -> service.delete(7L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已生效");
            verify(hourlyRateRepo, never()).delete(any());
        }

        @Test
        @DisplayName("delete: effectiveMonth > 本月 → 真删")
        void delete_futureEffective_ok() {
            HourlyRate h = new HourlyRate();
            h.setId(7L);
            h.setEffectiveMonth(LocalDate.now().withDayOfMonth(1).plusMonths(3));
            when(hourlyRateRepo.findByIdWithUser(7L)).thenReturn(Optional.of(h));

            service.delete(7L, 1L);
            verify(hourlyRateRepo).delete(h);
        }
    }

    // ============================================================
    // RoleCostDefault CRUD
    // ============================================================
    @Nested
    @DisplayName("RoleCostDefault (6 角色档)")
    class RoleDefaultCrud {

        @Test
        @DisplayName("updateRoleDefault: code 不存在 → 404")
        void update_notFound_404() {
            RoleCostDefaultUpdateRequest req =
                    new RoleCostDefaultUpdateRequest("UNKNOWN", new BigDecimal("400.00"));
            when(roleDefaultRepo.findById("UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateRoleDefault(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("角色档不存在");
        }

        @Test
        @DisplayName("updateRoleDefault: rate<=0 → 400")
        void update_invalidRate() {
            RoleCostDefaultUpdateRequest req =
                    new RoleCostDefaultUpdateRequest("DEV", BigDecimal.ZERO);
            RoleCostDefault existing = new RoleCostDefault();
            existing.setCode("DEV");
            existing.setRate(new BigDecimal("450.00"));
            when(roleDefaultRepo.findById("DEV")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.updateRoleDefault(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("rate 必须 > 0");
        }

        @Test
        @DisplayName("updateRoleDefault: happy path")
        void update_ok() {
            RoleCostDefaultUpdateRequest req =
                    new RoleCostDefaultUpdateRequest("DEV", new BigDecimal("500.00"));
            RoleCostDefault d = new RoleCostDefault();
            d.setCode("DEV");
            d.setRate(new BigDecimal("450.00"));
            when(roleDefaultRepo.findById("DEV")).thenReturn(Optional.of(d));
            when(roleDefaultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var item = service.updateRoleDefault(req);
            assertThat(item.rate()).isEqualByComparingTo("500");
        }
    }

    // ============================================================
    // CSV import
    // ============================================================
    @Nested
    @DisplayName("CSV 导入")
    class CsvImport {

        @Test
        @DisplayName("2 行正常 + 1 行坏行 (rate 非法) → ok=2 fail=1")
        void import_mixedRows() throws Exception {
            String csv = """
                    userId,username,roleCode,effectiveMonth,endMonth,rate,remark
                    ,zhangsan,DEV,2026-06,,600.00,角色档调价
                    1,,DEV,2026-06,2026-12,650.00,张三单人 override
                    1,,DEV,2026-06,,0.00,rate 非法
                    """;
            // 角色档行 userId=null,需绕过 userRepository
            when(hourlyRateRepo.findOverlap(eq(null), eq("DEV"), any(), any(), eq(null)))
                    .thenReturn(List.of());
            when(hourlyRateRepo.save(any())).thenAnswer(inv -> {
                HourlyRate h = inv.getArgument(0);
                h.setId(System.nanoTime());
                return h;
            });
            // 单人行 userId=1
            when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
            // 单人行也需 overlap 检查
            when(hourlyRateRepo.findOverlap(eq(1L), eq("DEV"), any(), any(), eq(null)))
                    .thenReturn(List.of());

            var res = service.importCsv(new StringReader(csv), 1L);
            assertThat(res.okCount()).isEqualTo(2);
            assertThat(res.failCount()).isEqualTo(1);
            assertThat(res.errors()).hasSize(1);
            assertThat(res.errors().get(0)).contains("rate 必须 > 0");
        }

        @Test
        @DisplayName("空文件 → 400")
        void import_empty() {
            assertThatThrownBy(() -> service.importCsv(new StringReader(""), 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("CSV 文件为空");
        }

        @Test
        @DisplayName("同 key (user_id=1, role=DEV, eff=2026-06) 二次出现: 取最后一行覆盖, 仍只生成 1 行")
        void import_dedupSameKey() throws Exception {
            String csv = """
                    userId,username,roleCode,effectiveMonth,endMonth,rate,remark
                    1,,DEV,2026-06,,500.00,第一次
                    1,,DEV,2026-06,,650.00,第二次覆盖
                    """;
            when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
            when(hourlyRateRepo.findOverlap(eq(1L), eq("DEV"), any(), any(), eq(null)))
                    .thenReturn(List.of());
            when(hourlyRateRepo.findAllByUser_IdOrderByEffectiveMonthDesc(1L))
                    .thenReturn(List.of(buildRate("DEV", "650.00", LocalDate.of(2026, 6, 1), "第二次覆盖")));

            var res = service.importCsv(new StringReader(csv), 1L);
            assertThat(res.okCount()).isEqualTo(2);
            assertThat(res.failCount()).isZero();
            // 最终落库的 1 条,rate 取最后一行 650
            var all = service.list(1L);
            assertThat(all).hasSize(1);
            assertThat(all.get(0).rate()).isEqualByComparingTo("650");
            assertThat(all.get(0).remark()).isEqualTo("第二次覆盖");
        }

        @Test
        @DisplayName("exportCsvTemplate: 7 列表头 + 1 行示例")
        void export_template() {
            String tpl = service.exportCsvTemplate();
            assertThat(tpl).contains("userId,username,roleCode,effectiveMonth,endMonth,rate,remark");
            assertThat(tpl).contains(",zhangsan,DEV,2026-06,,600.00");
        }

        /** 单人 override helper */
        private HourlyRate buildRate(String roleCode, String rate, LocalDate effective, String remark) {
            HourlyRate h = new HourlyRate();
            h.setId(99L);
            h.setRoleCode(roleCode);
            h.setUser(user);
            h.setRate(new BigDecimal(rate));
            h.setEffectiveMonth(effective);
            h.setRemark(remark);
            return h;
        }
    }
}