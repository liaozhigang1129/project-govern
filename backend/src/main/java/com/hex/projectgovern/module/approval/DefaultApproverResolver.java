package com.hex.projectgovern.module.approval;

import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认审批人解析: 支持 DEPT_LEAD/PMO_ADMIN/EXEC 三种 role
 * - DEPT_LEAD: 按 departmentId 找本部门 primary
 * - PMO_ADMIN/EXEC: 全局 primary
 * - 主审批人 disabled → 自动 fallback 到 backup (兼容既有立项审批语义)
 *
 * <p>从原 InitiationService.findStepUserId 抽取 (V6.0 审批引擎重构)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultApproverResolver implements ApproverResolver {

    private final UserRepository userRepository;

    @Override
    public Long resolve(String roleCode, Long departmentId, Long applicantId) {
        if (roleCode == null) return null;

        AppUser primary = "DEPT_LEAD".equals(roleCode) && departmentId != null
                ? userRepository.findFirstByDepartmentIdAndPrimaryRoleCodeAndDeletedFalse(departmentId, roleCode).orElse(null)
                : userRepository.findFirstByPrimaryRoleCodeAndDeletedFalse(roleCode).orElse(null);

        if (primary == null) return null;
        if (primary.isEnabled()) {
            if (primary.getId().equals(applicantId)) {
                log.warn("[ApproverResolver] 解析到申请人自己 ({}), 视为无审批人", applicantId);
                return null;
            }
            return primary.getId();
        }
        // 主审批人 disabled → fallback backup
        if (primary.getBackupUserId() == null) {
            log.warn("[ApproverResolver] 主审批人 {} disabled 无 backup 配置, fallback 失败", primary.getUsername());
            return null;
        }
        AppUser backup = userRepository.findByIdAndDeletedFalse(primary.getBackupUserId()).orElse(null);
        if (backup == null || !backup.isEnabled()) {
            log.warn("[ApproverResolver] backup {} 不可用, fallback 失败", primary.getBackupUserId());
            return null;
        }
        log.info("[ApproverResolver] 主审批人 {} disabled → {} 代审", primary.getUsername(), backup.getUsername());
        return backup.getId();
    }
}