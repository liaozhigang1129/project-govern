package com.company.zhiyu.module.org;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.common.security.RevokedTokenService;
import com.company.zhiyu.common.security.SecurityUtils;
import com.company.zhiyu.module.notification.MailService;
import com.company.zhiyu.module.org.dto.*;
import com.company.zhiyu.module.org.service.DeptPathResolver;
import com.company.zhiyu.module.project.ProjectRepository;
import com.company.zhiyu.module.wbs.WbsTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * L1-1 用户管理 Service
 * - 分页搜索 / 详情 / 新建 / 更新 / 重置密码 / 解锁 / 离职
 * - 多角色: 主角色 (primary_role_id) + user_role 兼任
 * - 自保护: 不能降级自己 / 不能停用最后一个 PMO_ADMIN
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    /** 密码强度: ≥10 位 + 小写 + 大写 + 数字 + 特殊字符 */
    private static final Pattern PWD_CORE =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,64}$");

    private final UserRepository userRepo;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final DepartmentRepository deptRepo;
    private final com.company.zhiyu.module.org.service.DeptPathResolver deptPath;
    private final ProjectRepository projectRepository;   // 注入 (同包,直接用)
    private final WbsTaskRepository wbsTaskRepository;   // 注入 (同包? 不, 在 wbs 包, 用 @Autowired)

    private final PasswordEncoder passwordEncoder;
    private final RevokedTokenService revokedTokenService;
    private final MailService mailService;
    private final SecurityUtils securityUtils;
    private final com.company.zhiyu.module.admin.SystemConfigService sysConfig;

    /** 委托给 SecurityUtils, 让 UserService 保持简洁 */
    private Long currentUserId() { return securityUtils.currentUserId(); }

    // ============================================================
    //  1) 分页搜索
    // ============================================================
    @Transactional(readOnly = true)
    public Page<UserListItem> search(String keyword, Long departmentId, String roleCode,
                                     boolean enabled, int page, int size, String sort) {
        Pageable pg = PageRequest.of(page, size, parseSort(sort));
        Page<AppUser> raw = userRepo.search(emp(keyword), departmentId, roleCode, enabled, pg);

        // 批量查部门名 (避免 N+1)
        Set<Long> deptIds = raw.stream().map(AppUser::getDepartmentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptRepo.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        // 批量查全路径 (V4.15)
        Map<Long, String> deptPaths = deptPath.batchResolve(deptIds);

        // 批量查角色 code
        Map<Long, List<String>> rolesByUser = new HashMap<>();
        for (AppUser u : raw) {
            List<Long> ids = userRoleRepo.findRoleIdsByUserId(u.getId());
            if (ids.isEmpty()) {
                rolesByUser.put(u.getId(), List.of());
                continue;
            }
            Map<Long, Role> byId = roleRepo.findAllById(ids).stream()
                    .collect(Collectors.toMap(Role::getId, r -> r));
            List<String> codes = ids.stream()
                    .map(rid -> Optional.ofNullable(byId.get(rid)).map(Role::getCode).orElse(null))
                    .filter(Objects::nonNull).toList();
            rolesByUser.put(u.getId(), codes);
        }

        return raw.map(u -> UserListItem.from(
                u,
                deptNames.getOrDefault(u.getDepartmentId(), ""),
                deptPaths.getOrDefault(u.getDepartmentId(), ""),
                rolesByUser.getOrDefault(u.getId(), List.of()),
                maskPhone(u.getPhone())));
    }

    // ============================================================
    //  1.5) V4.14 按部门树筛选 (含子部门)
    // ============================================================
    public Page<UserListItem> searchByDepartments(String keyword, List<Long> departmentIds,
                                                  boolean includeSubDepts, int page, int size, String sort) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return search(keyword, null, null, true, page, size, sort);
        }
        Pageable pg = PageRequest.of(page, size, parseSort(sort));
        List<Long> allDeptIds = new java.util.ArrayList<>(departmentIds);
        if (includeSubDepts) {
            // 拉所有 descendant 部门 ID, 用 tree_path LIKE 前缀
            for (Long did : departmentIds) {
                deptRepo.findById(did).ifPresent(d -> {
                    if (d.getTreePath() != null) {
                        // 走自定义查询: tree_path LIKE :path%
                        List<Department> subs = deptRepo.findDescendants(d.getTreePath());
                        for (Department s : subs) {
                            if (!allDeptIds.contains(s.getId())) {
                                allDeptIds.add(s.getId());
                            }
                        }
                    }
                });
            }
        }
        if (allDeptIds.isEmpty()) {
            return search(keyword, null, null, true, page, size, sort);
        }
        Page<AppUser> raw = userRepo.findByDepartmentIdInAndDeletedFalse(allDeptIds, pg);
        // 批量查部门名
        Set<Long> dIds = raw.stream().map(AppUser::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptRepo.findAllById(dIds)
                .stream().collect(Collectors.toMap(Department::getId, Department::getName));
        // V4.15: 全路径
        Map<Long, String> deptPaths = deptPath.batchResolve(dIds);
        return raw.map(u -> {
            String deptName = u.getDepartmentId() == null ? "" : deptNames.getOrDefault(u.getDepartmentId(), "");
            String deptPath_ = u.getDepartmentId() == null ? "" : deptPaths.getOrDefault(u.getDepartmentId(), "");
            List<String> roleCodes = userRoleRepo.findRoleIdsByUserId(u.getId()).stream()
                    .map(rid -> roleRepo.findById(rid).map(Role::getCode).orElse(null))
                    .filter(Objects::nonNull).toList();
            boolean isSelf = Objects.equals(currentUserId(), u.getId());
            return UserListItem.from(u, deptName, deptPath_, roleCodes, isSelf ? u.getPhone() : maskPhone(u.getPhone()));
        });
    }

    @Transactional(readOnly = true)
    public UserListItem detail(Long id) {
        AppUser u = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "USER.NOT_FOUND"));
        String deptName = Optional.ofNullable(u.getDepartmentId())
                .flatMap(did -> deptRepo.findById(did).map(Department::getName)).orElse("");
        String deptPath_ = Optional.ofNullable(u.getDepartmentId())
                .map(did -> deptPath.batchResolve(Set.of(did)).getOrDefault(did, ""))
                .orElse("");
        List<String> roleCodes = userRoleRepo.findRoleIdsByUserId(u.getId()).stream()
                .map(rid -> roleRepo.findById(rid).map(Role::getCode).orElse(null))
                .filter(Objects::nonNull).toList();
        boolean isSelf = Objects.equals(currentUserId(), u.getId());
        return UserListItem.from(u, deptName, deptPath_, roleCodes,
                isSelf ? u.getPhone() : maskPhone(u.getPhone()));
    }

    // ============================================================
    //  3) 新建
    // ============================================================
    @Transactional
    public AppUser create(UserCreateRequest req) {
        validateUsername(req.username());
        validatePassword(req.initialPassword());
        if (userRepo.existsByUsername(req.username()))
            throw new BusinessException(409, "USER.USERNAME_TAKEN");
        if (req.email() != null && !req.email().isBlank()
                && userRepo.existsByEmailAndDeletedFalse(req.email()))
            throw new BusinessException(409, "USER.EMAIL_TAKEN");
        if (deptRepo.findById(req.departmentId()).isEmpty())
            throw new BusinessException(404, "DEPT.NOT_FOUND");
        Role primary = roleRepo.findById(req.primaryRoleId())
                .orElseThrow(() -> new BusinessException(404, "ROLE.NOT_FOUND"));

        AppUser u = new AppUser();
        u.setUsername(req.username());
        u.setPasswordHash(passwordEncoder.encode(req.initialPassword()));
        u.setFullName(req.fullName());
        u.setEmail(req.email());
        u.setPhone(req.phone());
        u.setDepartmentId(req.departmentId());
        u.setPrimaryRole(primary);
        u.setJobTitle(req.jobTitle());
        u.setEnabled(req.enabled() == null ? true : req.enabled());
        u.setMustChangePassword(req.mustChangePassword() == null
                ? true : req.mustChangePassword());
        u.setPasswordChangedAt(Instant.now());
        u.setBackupUserId(req.backupUserId());
        AppUser saved = userRepo.save(u);

        // 多角色: 主角色 + 附加
        Set<Long> roleIds = new LinkedHashSet<>();
        roleIds.add(primary.getId());
        if (req.roleIds() != null) roleIds.addAll(req.roleIds());
        Long grantedBy = currentUserId() == null ? 1L : currentUserId();
        List<UserRole> urs = roleIds.stream().map(rid -> UserRole.builder()
                .grantedAt(java.time.Instant.now()).grantedBy(currentUserId())
                .userId(saved.getId()).roleId(rid).grantedBy(grantedBy).build()).toList();
        userRoleRepo.saveAll(urs);

        log.info("USER.CREATE id={} username={} roles={}",
                saved.getId(), saved.getUsername(), roleIds);
        return saved;
    }

    // ============================================================
    //  4) 更新
    // ============================================================
    @Transactional
    public AppUser update(Long id, UserUpdateRequest req) {
        AppUser u = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "USER.NOT_FOUND"));

        // 自保护
        if (Objects.equals(u.getId(), currentUserId())) {
            if (req.enabled() != null && !req.enabled())
                throw new BusinessException(409, "USER.SELF_LOCK");
            if (req.primaryRoleId() != null) {
                Role newPrimary = roleRepo.findById(req.primaryRoleId())
                        .orElseThrow(() -> new BusinessException(404, "ROLE.NOT_FOUND"));
                if (!"PMO_ADMIN".equals(newPrimary.getCode()))
                    throw new BusinessException(409, "USER.SELF_DEMOTE");
            }
        }

        // 防"最后一个 PMO_ADMIN"被停用/降级
        boolean isAdmin = "PMO_ADMIN".equals(u.getPrimaryRole().getCode());
        boolean willBeDisabled = req.enabled() != null && !req.enabled();
        boolean willDemote = req.primaryRoleId() != null
                && !Objects.equals(req.primaryRoleId(), u.getPrimaryRole().getId())
                && isAdmin;
        if ((willBeDisabled || willDemote)
                && userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse("PMO_ADMIN", true) <= 1)
            throw new BusinessException(409, "USER.LAST_ADMIN");

        // 应用变更
        if (req.fullName() != null)        u.setFullName(req.fullName());
        if (req.email() != null)          u.setEmail(req.email());
        if (req.phone() != null)          u.setPhone(req.phone());
        if (req.departmentId() != null)   u.setDepartmentId(req.departmentId());
        if (req.jobTitle() != null)       u.setJobTitle(req.jobTitle());
        if (req.enabled() != null)        u.setEnabled(req.enabled());
        if (req.mustChangePassword() != null) u.setMustChangePassword(req.mustChangePassword());
        if (req.backupUserId() != null)   u.setBackupUserId(req.backupUserId());
        if (req.primaryRoleId() != null) {
            u.setPrimaryRole(roleRepo.findById(req.primaryRoleId())
                    .orElseThrow(() -> new BusinessException(404, "ROLE.NOT_FOUND")));
        }
        // 多角色整体替换
        if (req.roleIds() != null) {
            userRoleRepo.deleteAllByUserId(u.getId());
            Set<Long> ids = new LinkedHashSet<>(req.roleIds());
            ids.add(u.getPrimaryRole().getId());
            Long grantedBy = currentUserId() == null ? 1L : currentUserId();
            userRoleRepo.saveAll(ids.stream().map(rid -> UserRole.builder()
                .grantedAt(java.time.Instant.now()).grantedBy(currentUserId())
                    .userId(u.getId()).roleId(rid).grantedBy(grantedBy).build()).toList());
        }

        // 停用 → 立即吊销 token
        if (willBeDisabled) {
            revokedTokenService.revokeAllByUserId(u.getId());
        }
        return userRepo.save(u);
    }

    // ============================================================
    //  5) 重置密码 (admin 重置)
    // ============================================================
    @Transactional
    public void resetPassword(Long id, PasswordResetRequest req) {
        validatePassword(req.newPassword());
        AppUser u = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "USER.NOT_FOUND"));
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        u.setPasswordChangedAt(Instant.now());
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        u.setMustChangePassword(req.mustChangeOnNextLogin() == null
                ? true : req.mustChangeOnNextLogin());
        userRepo.save(u);
        // 吊销该用户所有 token
        revokedTokenService.revokeAllByUserId(u.getId());
        // 发邮件 (可选)
        boolean notify = req.notifyByEmail() == null ? true : req.notifyByEmail();
        if (notify && u.getEmail() != null && !u.getEmail().isBlank()) {
            try {
                mailService.sendTo(u.getEmail(), "密码重置通知",
                        "您的密码已被管理员重置, 请立即登录并修改。\n" +
                        "用户名: " + u.getUsername() + "\n" +
                        "新密码: " + req.newPassword() + "\n" +
                        "登录地址: " + System.getenv().getOrDefault("APP_BASE_URL", "http://localhost:5173"));
            } catch (Exception e) {
                log.warn("resetPassword: 邮件发送失败 userId={} err={}", u.getId(), e.getMessage());
            }
        }
        log.info("USER.PASSWORD_RESET id={} by={}", u.getId(), currentUserId());
    }

    // ============================================================
    //  6) 改密 (用户自己)
    // ============================================================
    @Transactional
    public void changePassword(Long id, ChangePasswordRequest req) {
        AppUser u = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "USER.NOT_FOUND"));
        if (!passwordEncoder.matches(req.oldPassword(), u.getPasswordHash()))
            throw new BusinessException(401, "USER.INVALID_OLD_PASSWORD");
        validatePassword(req.newPassword());
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        u.setPasswordChangedAt(Instant.now());
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        u.setMustChangePassword(false);
        userRepo.save(u);
        log.info("USER.PASSWORD_CHANGED id={}", u.getId());
    }

    // ============================================================
    //  7) 解锁
    // ============================================================
    @Transactional
    public void unlock(Long id) {
        AppUser u = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "USER.NOT_FOUND"));
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        userRepo.save(u);
        log.info("USER.UNLOCKED id={} by={}", u.getId(), currentUserId());
    }

    // ============================================================
    //  8) 离职 (软删 + 交接 owner)
    // ============================================================
    @Transactional
    public void offboard(Long id, OffboardRequest req) {
        AppUser u = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "USER.NOT_FOUND"));
        if (Objects.equals(u.getId(), currentUserId()))
            throw new BusinessException(409, "USER.SELF_LOCK");
        if (Objects.equals(u.getId(), req.transferToUserId()))
            throw new BusinessException(422, "USER.SAME_TRANSFER_TARGET");
        if (!userRepo.findByIdAndDeletedFalse(req.transferToUserId())
                .map(AppUser::isEnabled).orElse(false))
            throw new BusinessException(422, "USER.TRANSFER_TARGET_DISABLED");

        Long transferTo = req.transferToUserId();

        // (1) 项目 owner 交接
        projectRepository.reassignPm(u.getId(), transferTo);
        // (2) WBS 任务 owner 交接
        wbsTaskRepository.reassignOwner(u.getId(), transferTo);
        // (3) 备选审批人清空
        userRepo.clearBackupUserId(u.getId());
        // (4) 软删 + 停用
        u.setEnabled(false);
        u.setDeleted(true);
        userRepo.save(u);
        // (5) 吊销 token
        revokedTokenService.revokeAllByUserId(u.getId());

        log.info("USER.OFFBOARD id={} -> transferTo={} reason={} by={}",
                u.getId(), transferTo, req.reason(), currentUserId());
    }

    // ============================================================
    //  9) V4.12: 批量操作
    // ============================================================

    /**
     * 批量启停 — 单 SQL,内置保护(自己不能停 / 不能停光所有 PMO_ADMIN)
     */
    @Transactional
    public Map<String, Object> bulkSetEnabled(List<Long> ids, boolean enabled) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("affected", 0, "skipped", List.of());
        }
        // 自保护 + 最后 PMO_ADMIN 护栏
        final List<Long> filtered = ids.stream()
                .filter(id -> !Objects.equals(id, currentUserId()))
                .toList();
        if (!enabled) {
            long adminCount = userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse("PMO_ADMIN", true);
            if (adminCount <= 1) {
                AppUser lastAdmin = userRepo.findFirstByPrimaryRoleCodeAndDeletedFalse("PMO_ADMIN").orElse(null);
                final List<Long> skipped = lastAdmin != null && filtered.contains(lastAdmin.getId())
                        ? List.of(lastAdmin.getId())
                        : List.of();
                if (!skipped.isEmpty()) {
                    List<Long> filtered2 = filtered.stream().filter(id -> !skipped.contains(id)).toList();
                    return Map.of("affected", userRepo.bulkSetEnabled(filtered2, enabled), "skipped", skipped);
                }
            }
        }
        int n = userRepo.bulkSetEnabled(filtered, enabled);
        if (!enabled) {
            filtered.forEach(revokedTokenService::revokeAllByUserId);
        }
        return Map.of("affected", n, "skipped",
                ids.stream().filter(id -> !filtered.contains(id)).toList());
    }

    /** 批量调整部门 */
    @Transactional
    public int bulkSetDepartment(List<Long> ids, Long deptId) {
        if (deptId != null && deptRepo.findByIdAndDeletedFalse(deptId).isEmpty()) {
            throw new BusinessException(404, "DEPT.NOT_FOUND");
        }
        return userRepo.bulkSetDepartment(ids, deptId);
    }

    // ============================================================
    //  V4.14: 单个用户分配部门
    // ============================================================
    @Transactional
    public AppUser updateDepartment(Long userId, Long deptId) {
        AppUser u = userRepo.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(404, "USER.NOT_FOUND"));
        if (deptId != null && deptRepo.findByIdAndDeletedFalse(deptId).isEmpty()) {
            throw new BusinessException(404, "DEPT.NOT_FOUND");
        }
        u.setDepartmentId(deptId);
        return userRepo.save(u);
    }

    /** V4.14: 未分配部门的用户 (分页) */
    @Transactional(readOnly = true)
    public Page<UserListItem> findUsersWithoutDepartment(int page, int size) {
        Pageable pg = PageRequest.of(page, size);
        Page<AppUser> raw = userRepo.findByDepartmentIdIsNullAndDeletedFalse(pg);
        return raw.map(u -> {
            List<String> roleCodes = userRoleRepo.findRoleIdsByUserId(u.getId()).stream()
                    .map(rid -> roleRepo.findById(rid).map(Role::getCode).orElse(null))
                    .filter(Objects::nonNull).toList();
            boolean isSelf = Objects.equals(currentUserId(), u.getId());
            return UserListItem.from(u, "", "", roleCodes, isSelf ? u.getPhone() : maskPhone(u.getPhone()));
        });
    }
    /** 批量解锁 */
    @Transactional
    public int bulkUnlock(List<Long> ids) {
        int n = userRepo.bulkUnlock(ids);
        ids.forEach(revokedTokenService::revokeAllByUserId);
        return n;
    }

    // ============================================================
    //  工具方法
    // ============================================================
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone == null ? "" : phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String emp(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static void validateUsername(String u) {
        if (u == null || !u.matches("^[a-z][a-z0-9._-]{2,31}$"))
            throw new BusinessException(422, "USER.INVALID_USERNAME");
    }

    private void validatePassword(String p) {
        if (p == null) throw new BusinessException(422, "USER.WEAK_PASSWORD");
        int minLen = sysConfig.getInt("security.password.min_length", 10);
        if (p.length() < minLen) throw new BusinessException(422, "USER.WEAK_PASSWORD (>= " + minLen + ")");
        if (p.length() > 64) throw new BusinessException(422, "USER.WEAK_PASSWORD (<= 64)");
        if (!PWD_CORE.matcher(p).matches()) throw new BusinessException(422, "USER.WEAK_PASSWORD (need upper+lower+digit+special)");
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.by(Sort.Direction.DESC, "id");
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction dir = parts.length > 1
                && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        // 白名单字段
        Set<String> allow = Set.of("id", "username", "fullName", "createdAt", "lastLoginAt");
        if (!allow.contains(field)) field = "id";
        return Sort.by(dir, field);
    }

    // === 由 Spring 注入的协作 Bean (其他模块) ===

    // ============================================================
    //  V4.36: 导出 Excel (账号/姓名/部门全路径/手机/邮箱/岗位)
    // ============================================================
    /**
     * 拉满足条件的全部用户, 写入 xlsx, 返回字节数组.
     *
     * @param keyword       关键字 (账号/姓名/邮箱/手机)
     * @param departmentId  部门 ID
     * @param roleCode      角色 code
     * @param enabled       enabled 标记 (列表默认 true)
     * @param limit         安全上限 (默认 10 万)
     * @return xlsx 二进制
     */
    public byte[] exportXlsx(String keyword, Long departmentId, String roleCode,
                             boolean enabled, int limit) throws java.io.IOException {
        // 1) 拉数据 — 复用 search 的查询语义, 但不走分页, 一次性拉满
        Pageable pg = PageRequest.of(0, Math.min(limit, 100_000), parseSort("id,desc"));
        Page<AppUser> raw = userRepo.search(emp(keyword), departmentId, roleCode, enabled, pg);

        // 2) 批量解析部门全路径 (复用 DeptPathResolver, 避免 N+1)
        Set<Long> deptIds = raw.stream().map(AppUser::getDepartmentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptPaths = deptPath.batchResolve(deptIds);

        // 3) 写 xlsx (SXSSF 流式, 内存占用仅 sheet 行缓冲 100 行)
        try (SXSSFWorkbook wb = new SXSSFWorkbook(new XSSFWorkbook(), 100, true)) {
            // 强制先写表头样式 (V4.36: 表头加粗 + 浅蓝底)
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            SXSSFSheet sheet = wb.createSheet("用户列表");
            // 列宽 (Excel 字符宽度单位)
            int[] widths = {16, 16, 32, 14, 28, 18};
            String[] headers = {"账号", "姓名", "部门(全路径)", "手机", "邮箱", "岗位"};
            for (int c = 0; c < headers.length; c++) {
                sheet.setColumnWidth(c, widths[c] * 256);
            }

            // 表头行
            var headerRow = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                var cell = headerRow.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headerStyle);
            }

            // 数据行
            int rowNum = 1;
            for (AppUser u : raw) {
                var row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(nz(u.getUsername()));
                row.createCell(1).setCellValue(nz(u.getFullName()));
                row.createCell(2).setCellValue(nz(u.getDepartmentId() == null
                        ? ""
                        : deptPaths.getOrDefault(u.getDepartmentId(), "")));
                row.createCell(3).setCellValue(nz(u.getPhone()));
                row.createCell(4).setCellValue(nz(u.getEmail()));
                row.createCell(5).setCellValue(nz(u.getJobTitle()));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            wb.write(out);
            wb.dispose();
            log.info("[UserExport] 导出用户 xlsx, 命中 {} 条, size={} bytes",
                    raw.getNumberOfElements(), out.size());
            return out.toByteArray();
        }
    }

    /** null 安全字符串 (空值落空串, 避免 Excel 写 null) */
    private static String nz(String s) { return s == null ? "" : s; }
}
