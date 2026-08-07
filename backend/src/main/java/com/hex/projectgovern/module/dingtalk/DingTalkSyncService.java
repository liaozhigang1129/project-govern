package com.hex.projectgovern.module.dingtalk;

import com.hex.projectgovern.module.org.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 钉钉通讯录同步服务 (V2.13 Phase 1)
 *
 * 入口: syncNow(triggerType, triggeredBy)  同步整家公司
 * 流程:
 *   1) 拉所有部门 ID (BFS 从 root=1)
 *   2) 对每个部门: getDept() 拿 name/parent_id → upsert Department (按 dingtalk_dept_id 匹配)
 *   3) 对每个部门: listDeptUsers() → 字段映射 → upsert AppUser (按 dingtalk_user_id 匹配)
 *   4) 离职检测: PMO 里有 dingtalk_user_id 但本次没出现的 → enabled=false
 *   5) 写 dingtalk_sync_log
 *
 * 同步策略 (用户已拍板):
 *  - 同步范围: 全公司
 *  - 冲突处理: 钉钉 source-of-truth, 覆盖 PMO 中的姓名/手机/邮箱/部门/职位/enabled
 *  - PMO 独有字段 (passwordHash, mustChangePassword, loginFailCount, defaultHourlyRate 等) 保留
 *  - 新员工自动建账号 (默认密码 = "DT:"+appSecret 计算的 SHA-256, 强制首次登录改密)
 *
 * 角色不变量 (CRITICAL — 任何人不许改):
 *  - 已存在用户的 primaryRole / user_role_assignments 在同步中**完全不被读写**
 *  - 仅当用户不存在、且 DingTalkProperties.autoCreate=true 时, 把 default role (字典序最小) 作为初始 primary role
 *  - 该 primary role 仅给 "登录后能进系统" 用, 不能据此推断 PMO 业务授权;后续权限请走 UserRoleAdminController 单独配置
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DingTalkSyncService {

    private final DingTalkProperties props;
    private final DingTalkApiClient api;
    private final DingTalkSyncLogRepository logRepo;
    private final DingTalkUserLookupRepository userLookup;
    private final DingTalkDepartmentLookupRepository deptLookup;
    private final DingTalkRoleLookupRepository roleLookup;
    private final UserRepository userRepo;
    private final DepartmentRepository deptRepo;
    private final RoleRepository roleRepo;

    /** 钉钉根部门 ID (企业内部应用, 默认 = 1) */
    private static final long ROOT_DEPT_ID = 1L;

    // ============================================================
    // 同步入口
    // ============================================================
    public DingTalkSyncLog syncNow(String triggerType, String triggeredBy) {
        // 1) 立即创建 RUNNING log (非阻塞), 让客户端可轮询
        DingTalkSyncLog slog = new DingTalkSyncLog();
        slog.setStartedAt(Instant.now());
        slog.setTriggerType(triggerType);
        slog.setTriggeredBy(triggeredBy);
        slog.setStatus("RUNNING");
        final DingTalkSyncLog running = logRepo.save(slog);
        log.info("[DingTalkSync] 触发异步同步, logId={} trigger={} by={}",
                running.getId(), triggerType, triggeredBy);

        // 2) 启动后台线程执行实际同步 (不阻塞 HTTP 请求)
        Thread async = new Thread(() -> {
            try {
                runSync(running.getId(), triggerType, triggeredBy);
            } catch (Exception ex) {
                log.error("[DingTalkSync] 后台同步线程异常", ex);
            }
        }, "dingtalk-sync-" + running.getId());
        async.setDaemon(true);
        async.start();

        // 3) 立即返回 RUNNING 状态
        return running;
    }

    /**
     * 实际执行同步 (后台线程, 阻塞). 完成后更新 log 的 status/finished_at.
     * @param logId sync_log.id
     */
    private void runSync(Long logId, String triggerType, String triggeredBy) {
        DingTalkSyncLog slog = logRepo.findById(logId).orElseThrow();
        DingTalkSyncLog running = slog;

        try {
            if (!props.canSync()) {
                throw new IllegalStateException("钉钉未启用或未配置: enabled=" + props.isEnabled() + ", configured=" + props.isConfigured());
            }

            // 1) 部门 (V4.14: 拓扑排序 + tree_path/level)
            // 步骤: 1) 收集所有 DeptInfo
            //       2) 拓扑排序 (按 parentId 升序, 保证父在前)
            //       3) 第一遍: upsert (parent_id 暂为 null, 因为可能父还没入库)
            //       4) 第二遍: 设 parent_id (java self-ref) + tree_path/level
            // 直接用 listAllDepts 一次性拿到所有 dept (带 parent_id), 不再逐个 getDept
            List<DingTalkApiClient.DeptInfo> allInfo = api.listAllDepts();
            running.setTotalDepts(allInfo.size());
            List<Long> deptIds = allInfo.stream().map(DingTalkApiClient.DeptInfo::deptId).toList();
            // 1.2 拓扑排序: 根(parentId=0 或 ROOT_DEPT_ID) 在前, 子在后
            allInfo.sort(Comparator.comparingLong(d -> d.parentId() == 0 ? -1 : d.parentId()));

            // 1.3 第一遍: upsert, parent_id 暂为 null
            Map<Long, Department> dtToJava = new HashMap<>();  // 钉钉 deptId -> Java Department
            for (DingTalkApiClient.DeptInfo info : allInfo) {
                Department d = upsertDepartment(info, dtToJava);
                dtToJava.put(info.deptId(), d);
            }
            // 1.4 第二遍: 设 parent_id (java self-ref) + tree_path/level
            int createdDepts = 0;
            for (DingTalkApiClient.DeptInfo info : allInfo) {
                Department d = dtToJava.get(info.deptId());
                if (d == null) continue;
                // parent 解析
                // 关键修复: 钉钉子部门 parent_id=1 (即根) 时, 应该 parent = Java 根
                // 去掉 ROOT_DEPT_ID 判断, 这样所有子部门都能正确找到父
                Department parent = null;
                if (info.parentId() != 0L) {
                    parent = dtToJava.get(info.parentId());
                }
                d.setParentId(parent != null ? parent.getId() : null);
                d.setDingtalkParentId(info.parentId() != 0 ? info.parentId() : null);
                // tree_path / tree_level
                if (parent == null) {
                    d.setTreePath("/" + info.deptId() + "/");
                    d.setTreeLevel(0);
                } else {
                    d.setTreePath(parent.getTreePath() + info.deptId() + "/");
                    d.setTreeLevel(parent.getTreeLevel() + 1);
                }
                if (d.getCreatedAt() != null && d.getCreatedAt().isAfter(running.getStartedAt().minusSeconds(60))) {
                    createdDepts++;
                }
                deptRepo.save(d);
            }
            running.setCreatedDeptCount(createdDepts);
            // updated_dept 不严格算 (因为 createdAt 不一定准确反映 upsert), 暂用 dtToJava.size - created

            // 2) 用户
            Set<String> syncedUserIds = new HashSet<>();
            for (Long deptId : deptIds) {
                List<JsonNode> users = api.listDeptUsers(deptId);
                for (JsonNode u : users) {
                    String dtUserId = u.path("userid").asText("");
                    if (dtUserId.isEmpty()) continue;
                    if (!syncedUserIds.add(dtUserId)) continue;  // 同一员工可能在多部门, 只处理一次
                    upsertUser(u, dtToJava);
                }
            }
            running.setTotalUsers(syncedUserIds.size());

            // 3) 离职检测
            int disabled = disableMissingUsers(syncedUserIds);
            running.setDisabledCount(disabled);

            // 4) 完成
            running.setStatus("SUCCESS");
            running.setFinishedAt(Instant.now());
            logRepo.save(slog);
            log.info("[DingTalkSync] SUCCESS depts={} users={} disabled={}",
                    deptIds.size(), syncedUserIds.size(), disabled);
        } catch (Exception e) {
            log.error("[DingTalkSync] FAILED", e);
            running.setStatus("FAILED");
            running.setFinishedAt(Instant.now());
            running.setErrorMessage(e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            running.setErrorDetail(sw.toString().substring(0, Math.min(4000, sw.toString().length())));
            logRepo.save(slog);
        }
    }

    // ============================================================
    // 部门 upsert
    // ============================================================
    @Transactional
    protected Department upsertDepartment(DingTalkApiClient.DeptInfo info, Map<Long, Department> ctx) {
        // 同步场景要"复活"软删记录: 钉钉 dept 短暂消失再回来时, code/dingtalk_dept_id 是同一个,
        // 不能新建 (会撞 partial unique 的 code/diqngtalk_dept_id), 直接复用旧行。
        Optional<Department> existing = deptLookup.findByDingtalkDeptIdIncludingDeleted(info.deptId());
        Department d = existing.orElseGet(() -> {
            Department nd = new Department();
            nd.setDingtalkDeptId(info.deptId());
            nd.setEnabled(true);
            nd.setSortOrder(0);
            return nd;
        });
        // 复活软删记录 — 仅同步路径, 不影响手动软删的 dept (那些没绑 dingtalk_dept_id, 走不到这里)
        if (d.isDeleted()) {
            d.setDeleted(false);
            d.setEnabled(true);
        }
        d.setName(info.name());
        // code: 钉钉 dept_id 转字符串, 保证唯一
        d.setCode("DT-" + info.deptId());
        // parent_id 解析
        if (info.parentId() == 0L || info.deptId() == ROOT_DEPT_ID) {
            d.setParentId(null);
        } else {
            Department parent = ctx.get(info.parentId());
            d.setParentId(parent != null ? parent.getId() : null);
        }
        return deptRepo.save(d);
    }

    // ============================================================
    // 用户 upsert
    //
    // 不变量:
    //   - 已有用户: 只覆写 姓名/手机/邮箱/职位/部门/enabled/dingtalk_user_id;
    //               primaryRole / passwordHash / user_role_assignments 保持原样,
    //               不允许 setPrimaryRole.
    //   - 新用户: 若 autoCreate=true, 分配 default role(字典序最小)。
    //             该角色仅为"能登录"用, 业务授权必须经 UserRoleAdminController 单独配。
    // ============================================================
    @Transactional
    protected AppUser upsertUser(JsonNode u, Map<Long, Department> dtToJava) {
        String dtUserId = u.path("userid").asText();
        String name = u.path("name").asText("");
        String mobile = u.path("mobile").asText("");
        String email = u.path("email").asText(null);
        // 新接口 /topapi/v2/user/list 用 title; 老接口 /user/list 用 position; 都兼容
        String jobTitle = u.path("title").asText(u.path("position").asText(""));
        boolean active = u.path("active").asBoolean(true);

        Optional<AppUser> byDt = userLookup.findByDingtalkUserId(dtUserId);
        AppUser user = byDt.orElse(null);

        if (user == null && props.isAutoCreate()) {
            // 找 by phone 兜底 (用户可能先在 PMO 注册, 后绑钉钉)
            if (!mobile.isEmpty()) {
                user = userLookup.findByPhone(mobile).orElse(null);
            }
        }

        if (user == null) {
            if (!props.isAutoCreate()) {
                log.info("[DingTalkSync] skip create (auto_create=false) dtUserId={}", dtUserId);
                return null;
            }
            user = new AppUser();
            // username: 优先用 mobile, 否则用 dt userid
            user.setUsername(mobile.isEmpty() ? dtUserId : mobile);
            // 默认密码: DT:<userid> 强制首次登录改密
            user.setPasswordHash(sha256("DT:" + dtUserId + ":" + props.getAppSecret()));
            user.setMustChangePassword(true);
            // 默认角色
            roleLookup.findDefaultRole().ifPresent(user::setPrimaryRole);
        }

        // 覆盖字段 (source-of-truth)
        if (!name.isEmpty()) user.setFullName(name);
        if (!mobile.isEmpty()) user.setPhone(mobile);
        if (email != null && !email.isEmpty()) user.setEmail(email);
        if (jobTitle != null) user.setJobTitle(jobTitle);
        user.setDingtalkUserId(dtUserId);
        user.setEnabled(active);

        // 部门: 取 dept_id_list 第 1 个
        JsonNode deptList = u.path("dept_id_list");
        if (deptList.isArray() && deptList.size() > 0) {
            Long primaryDeptId = deptList.get(0).asLong();
            Department d = dtToJava.get(primaryDeptId);
            if (d != null) user.setDepartmentId(d.getId());
        }

        return userRepo.save(user);
    }

    // ============================================================
    // 离职禁用
    // ============================================================
    @Transactional
    protected int disableMissingUsers(Set<String> syncedIds) {
        List<AppUser> bound = userLookup.findDingtalkBoundActive();
        int n = 0;
        for (AppUser u : bound) {
            if (!syncedIds.contains(u.getDingtalkUserId()) && u.isEnabled()) {
                u.setEnabled(false);
                userRepo.save(u);
                n++;
                log.info("[DingTalkSync] disabled (离职) userId={} dtUserId={}", u.getId(), u.getDingtalkUserId());
            }
        }
        return n;
    }

    // ============================================================
    // util
    // ============================================================
    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "DT-DISABLED";
        }
    }
}

// 需要 import StringWriter + PrintWriter
