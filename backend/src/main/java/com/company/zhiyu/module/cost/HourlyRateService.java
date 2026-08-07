package com.company.zhiyu.module.cost;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.cost.dto.*;
import com.company.zhiyu.module.org.AppUser;
import com.company.zhiyu.module.org.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * P0-A.1 工时费率管理服务 — F1 工时→成本引擎
 *
 * <p>职责:
 * <ol>
 *   <li>CRUD HourlyRate (单人 override + 角色档)</li>
 *   <li>CRUD RoleCostDefault (6 角色档默认价)</li>
 *   <li><b>resolveRate</b> — 4 级兜底费率解析 (核心算法)</li>
 *   <li>CSV 上传 / 模板下载</li>
 * </ol>
 *
 * <p>费率解析优先级 (P0-A.1):
 * <pre>
 *   1) hourly_rate.user_id = uid && 当月生效       → USER_OVERRIDE
 *   2) hourly_rate.role_code = role && user_id IS NULL && 当月生效 → ROLE_OVERRIDE
 *   3) role_cost_default.code = role               → ROLE_COST_DEFAULT
 *   4) app_user.default_hourly_rate                → USER_DEFAULT
 *   5) 0                                            → NONE (fallback)
 * </pre>
 *
 * @since V4.0 (2026-Q2)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HourlyRateService {

    private final HourlyRateRepository hourlyRateRepo;
    private final RoleCostDefaultRepository roleDefaultRepo;
    private final UserRepository userRepository;

    // ============================================================
    // RoleCostDefault (6 角色档)
    // ============================================================

    @Transactional(readOnly = true)
    public List<RoleCostDefaultItem> listRoleDefaults() {
        return roleDefaultRepo.findAllByOrderBySortOrderAscCodeAsc().stream()
                .map(RoleCostDefaultItem::from).toList();
    }

    @Transactional
    public RoleCostDefaultItem updateRoleDefault(RoleCostDefaultUpdateRequest req) {
        RoleCostDefault r = roleDefaultRepo.findById(req.code())
                .orElseThrow(() -> new BusinessException(404, "角色档不存在: " + req.code()));
        if (req.rate() == null || req.rate().signum() <= 0) {
            throw new BusinessException(400, "rate 必须 > 0, 实得: " + req.rate());
        }
        r.setRate(req.rate());
        log.info("[Cost] role default {} updated to {}", req.code(), req.rate());
        return RoleCostDefaultItem.from(roleDefaultRepo.save(r));
    }

    // ============================================================
    // HourlyRate CRUD
    // ============================================================

    @Transactional(readOnly = true)
    public List<HourlyRateItem> list(Long userId) {
        List<HourlyRate> rows = (userId == null)
                ? hourlyRateRepo.findAllByOrderByEffectiveMonthDescIdDesc()
                : hourlyRateRepo.findAllByUser_IdOrderByEffectiveMonthDesc(userId);
        return rows.stream().map(HourlyRateItem::from).toList();
    }

    @Transactional(readOnly = true)
    public HourlyRateItem get(Long id) {
        HourlyRate h = hourlyRateRepo.findByIdWithUser(id)
                .orElseThrow(() -> new BusinessException(404, "费率不存在: " + id));
        return HourlyRateItem.from(h);
    }

    @Transactional
    public HourlyRateItem create(HourlyRateUpsertRequest req, Long operatorId) {
        req.validate();
        HourlyRate h = new HourlyRate();
        req.toEntity(h);
        if (req.userId() != null) {
            AppUser u = userRepository.findByIdAndDeletedFalse(req.userId())
                    .orElseThrow(() -> new BusinessException(400, "userId 不存在: " + req.userId()));
            h.setUser(u);
        }
        h.setCreatedBy(operatorId);
        validateNoOverlap(h, null);
        HourlyRate saved = hourlyRateRepo.save(h);
        log.info("[Cost] hourly_rate created id={} role={} user={} rate={} from={} to={} by={}",
                saved.getId(), saved.getRoleCode(),
                saved.getUser() == null ? null : saved.getUser().getId(),
                saved.getRate(), saved.getEffectiveMonth(), saved.getEndMonth(), operatorId);
        return HourlyRateItem.from(saved);
    }

    @Transactional
    public HourlyRateItem update(Long id, HourlyRateUpsertRequest req, Long operatorId) {
        req.validate();
        HourlyRate h = hourlyRateRepo.findByIdWithUser(id)
                .orElseThrow(() -> new BusinessException(404, "费率不存在: " + id));
        req.toEntity(h);
        if (req.userId() != null) {
            AppUser u = userRepository.findByIdAndDeletedFalse(req.userId())
                    .orElseThrow(() -> new BusinessException(400, "userId 不存在: " + req.userId()));
            h.setUser(u);
        } else {
            h.setUser(null);
        }
        // 关停校验: 改 endMonth / effectiveMonth 时仍需避开重叠
        validateNoOverlap(h, id);
        HourlyRate saved = hourlyRateRepo.save(h);
        log.info("[Cost] hourly_rate updated id={} by={}", id, operatorId);
        return HourlyRateItem.from(saved);
    }

    /** 软关: endMonth = 上月末 (不清行, 保留历史) */
    @Transactional
    public HourlyRateItem close(Long id, YearMonth atMonth, Long operatorId) {
        if (atMonth == null) throw new BusinessException(400, "atMonth 必填");
        HourlyRate h = hourlyRateRepo.findByIdWithUser(id)
                .orElseThrow(() -> new BusinessException(404, "费率不存在: " + id));
        LocalDate end = atMonth.atEndOfMonth();
        if (h.getEffectiveMonth() != null && end.isBefore(h.getEffectiveMonth())) {
            throw new BusinessException(400, "关停月份(" + atMonth + ")早于生效月份("
                    + YearMonth.from(h.getEffectiveMonth()) + ")");
        }
        h.setEndMonth(end);
        log.info("[Cost] hourly_rate closed id={} endMonth={} by={}", id, end, operatorId);
        return HourlyRateItem.from(hourlyRateRepo.save(h));
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        // 仅"未生效"(effectiveMonth > 本月) 可删,已生效用 close
        HourlyRate h = hourlyRateRepo.findByIdWithUser(id)
                .orElseThrow(() -> new BusinessException(404, "费率不存在: " + id));
        LocalDate today = LocalDate.now().withDayOfMonth(1);
        if (h.getEffectiveMonth() != null && !h.getEffectiveMonth().isAfter(today)) {
            throw new BusinessException(400, "已生效的费率不能删除,请用关停(close)代替");
        }
        hourlyRateRepo.delete(h);
        log.info("[Cost] hourly_rate deleted id={} by={}", id, operatorId);
    }

    private void validateNoOverlap(HourlyRate h, Long excludeId) {
        LocalDate from = h.getEffectiveMonth();
        LocalDate to   = h.getEndMonth() == null ? LocalDate.of(9999, 12, 1) : h.getEndMonth();
        Long uid = h.getUser() == null ? null : h.getUser().getId();
        var overlaps = hourlyRateRepo.findOverlap(uid, h.getRoleCode(), from, to, excludeId);
        if (!overlaps.isEmpty()) {
            String firstId = overlaps.get(0).getId().toString();
            throw new BusinessException(400,
                    "费率区间冲突: 与已存在的 id=" + firstId + " 重叠 ["
                            + from + ", " + (h.getEndMonth() == null ? "∞" : to) + "]");
        }
    }

    // ============================================================
    // 核心: resolveRate — 4 级兜底
    // ============================================================

    /** 费率来源 (与前端 CostBreakdownItem.rateSource 字段对齐) */
    public enum RateSource {
        USER_OVERRIDE,       // hourly_rate.user_id 命中
        ROLE_OVERRIDE,       // hourly_rate.role_code 命中 (user_id IS NULL)
        ROLE_COST_DEFAULT,   // role_cost_default 字典
        USER_DEFAULT,        // app_user.default_hourly_rate
        NONE                 // 全 0
    }

    public record RateResolution(BigDecimal rate, RateSource source) {}

    /**
     * 给定 (userId, roleCode, month), 解析当月生效的费率。
     * <p>调用方 (CostEngineService) 不需要处理 null: rate 字段恒返回 BigDecimal (可能 ZERO)。
     *
     * @param userId   AppUser.id
     * @param roleCode 角色编码, 可为空 → 跳过第 1/2 级
     * @param month    YYYY-MM-01
     */
    @Transactional(readOnly = true)
    public RateResolution resolveRate(Long userId, String roleCode, LocalDate month) {
        Objects.requireNonNull(month, "month");

        // 1) USER_OVERRIDE
        if (userId != null) {
            var userRates = hourlyRateRepo.findActiveUserRate(userId, month);
            if (!userRates.isEmpty()) {
                return new RateResolution(userRates.get(0).getRate(), RateSource.USER_OVERRIDE);
            }
        }
        // 2) ROLE_OVERRIDE
        if (roleCode != null && !roleCode.isBlank()) {
            var roleRates = hourlyRateRepo.findActiveRoleRate(roleCode, month);
            if (!roleRates.isEmpty()) {
                return new RateResolution(roleRates.get(0).getRate(), RateSource.ROLE_OVERRIDE);
            }
            // 3) ROLE_COST_DEFAULT 字典
            var def = roleDefaultRepo.findById(roleCode).orElse(null);
            if (def != null) {
                return new RateResolution(def.getRate(), RateSource.ROLE_COST_DEFAULT);
            }
        }
        // 4) USER_DEFAULT
        if (userId != null) {
            AppUser u = userRepository.findByIdAndDeletedFalse(userId).orElse(null);
            if (u != null && u.getDefaultHourlyRate() != null && u.getDefaultHourlyRate().signum() > 0) {
                return new RateResolution(u.getDefaultHourlyRate(), RateSource.USER_DEFAULT);
            }
        }
        // 5) NONE
        return new RateResolution(BigDecimal.ZERO, RateSource.NONE);
    }

    // ============================================================
    // CSV 上传 — 复用 SystemConfigService 风格
    // ============================================================

    /** CSV 列: userId,username,roleCode,effectiveMonth(YYYY-MM),endMonth(YYYY-MM),rate,remark */
    public static final String[] CSV_HEADER = {
            "userId", "username", "roleCode", "effectiveMonth", "endMonth", "rate", "remark"
    };

    public record CsvRowResult(int okCount, int failCount, List<String> errors) {}

    /**
     * 解析 + 批量 upsert (一事务)。
     * - 角色档行 (userId 空) → 唯一键 (role_code, effective_month, user_id IS NULL) upsert
     * - 单人行 (userId 非空) → 唯一键 (user_id, role_code, effective_month) upsert
     * - 同 (key) 已存在 → 用新行替换 (保留 history 时不删旧行, 旧行 endMonth 自动回填)
     */
    @Transactional
    public CsvRowResult importCsv(java.io.Reader reader, Long operatorId) throws java.io.IOException {
        var br = new java.io.BufferedReader(reader);
        String header = br.readLine();
        if (header == null) throw new BusinessException(400, "CSV 文件为空");
        // 简化: 不严格校验列序,固定按位置读
        int ok = 0, fail = 0;
        var errors = new java.util.ArrayList<String>();
        String line;
        int lineNo = 1;
        Map<String, HourlyRate> keyIndex = new HashMap<>();
        while ((line = br.readLine()) != null) {
            lineNo++;
            if (line.isBlank()) continue;
            try {
                String[] cols = line.split(",", -1);
                if (cols.length < 6) throw new IllegalArgumentException("列数不足 6");
                String userIdStr = cols[0].trim();
                String roleCode  = cols[2].trim().toUpperCase();
                String effStr    = cols[3].trim();
                String endStr    = cols[4].trim();
                String rateStr   = cols[5].trim();
                String remark    = cols.length > 6 ? cols[6].trim() : null;

                Long userId = userIdStr.isEmpty() ? null : Long.parseLong(userIdStr);
                if (userId == null && (roleCode == null || roleCode.isBlank())) {
                    throw new IllegalArgumentException("userId 与 roleCode 至少填一个");
                }
                YearMonth eff = YearMonth.parse(effStr);
                YearMonth end = endStr.isEmpty() ? null : YearMonth.parse(endStr);
                BigDecimal rate = new BigDecimal(rateStr);
                if (rate.signum() <= 0) throw new IllegalArgumentException("rate 必须 > 0");

                HourlyRate h = new HourlyRate();
                h.setRoleCode(roleCode);
                h.setRate(rate);
                h.setEffectiveMonth(eff.atDay(1));
                h.setEndMonth(end == null ? null : end.atEndOfMonth());
                h.setRemark(remark);
                h.setCreatedBy(operatorId);
                if (userId != null) {
                    AppUser u = userRepository.findByIdAndDeletedFalse(userId)
                            .orElseThrow(() -> new IllegalArgumentException("userId 不存在: " + userId));
                    h.setUser(u);
                }
                // 单事务内同 key 合并: 二次出现视为同区间覆盖
                String key = (userId == null ? "_" : userId) + "|" + roleCode + "|" + eff;
                HourlyRate existing = keyIndex.get(key);
                if (existing != null) {
                    existing.setRate(rate);
                    existing.setEndMonth(end == null ? null : end.atEndOfMonth());
                    existing.setRemark(remark);
                } else {
                    validateNoOverlap(h, null);
                    keyIndex.put(key, hourlyRateRepo.save(h));
                }
                ok++;
            } catch (Exception e) {
                fail++;
                errors.add("line " + lineNo + ": " + e.getMessage());
            }
        }
        log.info("[Cost] CSV import: ok={}, fail={}, by={}", ok, fail, operatorId);
        return new CsvRowResult(ok, fail, errors);
    }

    public String exportCsvTemplate() {
        var sb = new StringBuilder();
        for (int i = 0; i < CSV_HEADER.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(CSV_HEADER[i]);
        }
        sb.append('\n');
        sb.append(",zhangsan,DEV,2026-06,,600.00,6月全员调薪 5%\n");
        sb.append("1,,DEV,2026-06,2026-12,650.00,张三单人 override\n");
        return sb.toString();
    }
}