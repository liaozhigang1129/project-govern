package com.hex.projectgovern.module.initiation;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.dict.ApprovalStep;
import com.hex.projectgovern.module.dict.ApprovalStepRepository;
import com.hex.projectgovern.module.dict.InitiationStatus;
import com.hex.projectgovern.module.dict.InitiationStatusRepository;
import com.hex.projectgovern.module.dict.ProjectStatusRepository;
import com.hex.projectgovern.module.dict.ProjectTypeRepository;
import com.hex.projectgovern.module.notification.InitiationDecidedEvent;
import com.hex.projectgovern.module.notification.InitiationResubmittedEvent;
import com.hex.projectgovern.module.notification.InitiationSubmittedEvent;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import com.hex.projectgovern.module.approval.ApprovalEngine;
import com.hex.projectgovern.module.approval.ApprovalFlowInstance;
import com.hex.projectgovern.module.project.Project;
import java.time.LocalDate;
import java.util.UUID;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitiationService {

    private final ProjectInitiationRepository initiationRepository;
    private final ApprovalRecordRepository approvalRepo;
    private final InitiationStatusRepository statusRepo;
    private final ApprovalStepRepository stepRepo;
    private final ProjectRepository projectRepository;
    private final ProjectTypeRepository typeRepo;
    private final ProjectStatusRepository projectStatusRepo;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final InitiationSowFileRepository sowFileRepository;
    private final InitiationAiWbsDraftRepository aiWbsDraftRepository;
    private final com.hex.projectgovern.module.approval.InitiationApprovalAdapter approvalAdapter;
    private final com.hex.projectgovern.module.approval.ApprovalFlowActionRepository approvalFlowActionRepo;
    private final ApprovalEngine approvalEngine;
    private final InitiationAiWbsService aiWbsService;

    /** SOW 贴文本最大长度(50KB,超出截断) */
    private static final int SOW_PASTE_MAX = 50 * 1024;

    private static final List<String> APPROVAL_FLOW = List.of("DEPT_LEAD", "PMO_ADMIN", "EXEC");

    public List<ProjectInitiation> list() {
        return initiationRepository.findAllActiveWithStatus();
    }

    /**
     * 带条件查询立项列表
     */
    /**
     * 软删除立项。
     * 规则:
     *  - 已关联项目 (project_id != null)  → 拒绝,提示先删项目
     *  - 状态为 EXEC_APPROVED             → 拒绝(终审通过的立项是审计凭据)
     *  - 其余状态允许: 申请人本人或管理员可调
     * 软删 (deleted=true) 而非硬删,保留历史审批流水。
     */
    @Transactional
    public void softDelete(Long initiationId, Long operatorUserId) {
        ProjectInitiation i = get(initiationId);
        if (i.getProjectId() != null) {
            throw new BusinessException("该立项已关联项目,请先删除项目");
        }
        if ("EXEC_APPROVED".equals(i.getStatus().getCode())) {
            throw new BusinessException("已批准 (EXEC_APPROVED) 的立项不允许删除");
        }
        AppUser operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new BusinessException(404, "User not found: " + operatorUserId));
        boolean isAdmin = operator.getPrimaryRole() != null
                && "ADMIN".equals(operator.getPrimaryRole().getCode());
        boolean isApplicant = java.util.Objects.equals(i.getApplicantId(), operatorUserId);
        if (!isAdmin && !isApplicant) {
            throw new BusinessException("仅申请人本人或管理员可删除该立项");
        }
        i.setDeleted(true);
        log.info("[Initiation] softDelete id={} code={} operator={} (admin={}, applicant={})",
                i.getId(), i.getCode(), operator.getUsername(), isAdmin, isApplicant);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ProjectInitiation> listWithFilters(
            String keyword,
            String statusCode,
            String currentStep,
            Long applicantId,
            Long departmentId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
        return initiationRepository.findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("deleted"), false));
            
            if (keyword != null && !keyword.isBlank()) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("code")), likePattern),
                    cb.like(cb.lower(root.get("title")), likePattern)
                ));
            }
            
            if (statusCode != null && !statusCode.isBlank()) {
                predicates.add(cb.equal(root.get("status").get("code"), statusCode));
            }
            
            if (currentStep != null && !currentStep.isBlank()) {
                predicates.add(cb.equal(root.get("currentStep"), currentStep));
            }
            
            if (applicantId != null) {
                predicates.add(cb.equal(root.get("applicantId"), applicantId));
            }
            
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("departmentId"), departmentId));
            }
            
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
            }
            
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), endDate.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant()));
            }
            
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        });
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ProjectInitiation get(Long id) {
        return initiationRepository.findById(id)
                .filter(i -> !i.isDeleted())
                .orElseThrow(() -> new BusinessException(404, "Initiation not found: " + id));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ProjectInitiation decideView(Long id) {
        return get(id);
    }

    /**
     * 立项审批流水 (WP-M7-07: 改读 ApprovalFlowAction, 替代 ApprovalRecord)
     * <p>对历史数据 (instanceId=null) 仍读老 ApprovalRecord (v5 数据迁移前)
     */
    public List<ApprovalRecord> records(Long initiationId) {
        ProjectInitiation i = get(initiationId);
        if (i.getApprovalInstanceId() == null) {
            // 老数据: 继续读 ApprovalRecord
            return approvalRepo.findByInitiationIdOrderByDecidedAtAsc(initiationId);
        }
        // 新数据: 走引擎 ApprovalFlowAction, 映射成 ApprovalRecord (前端 API 兼容)
        List<com.hex.projectgovern.module.approval.ApprovalFlowAction> actions =
            approvalFlowActionRepo.findByInstanceIdOrderByDecidedAtAsc(i.getApprovalInstanceId());
        List<ApprovalRecord> out = new java.util.ArrayList<>(actions.size());
        for (var a : actions) {
            ApprovalRecord r = new ApprovalRecord();
            r.setInitiationId(initiationId);
            r.setStepId(a.getStepNo() == null ? 0L : a.getStepNo().longValue());
            r.setApproverId(a.getApproverId());
            r.setOnBehalfOfUserId(a.getOnBehalfOfUserId());
            r.setDecision(a.getDecision() == null ? null : a.getDecision().name());
            r.setComment(a.getComment());
            r.setDecidedAt(a.getDecidedAt());
            out.add(r);
        }
        return out;
    }

    /**
     * SUPPLEMENT 状态的立项,申请人补材料后重新提交进入下一级审批。
     * 规则:
     *  - 仅当 status=SUPPLEMENT 且 currentStep 非空 时才允许
     *  - 状态置回 PENDING,currentStep 不变
     *  - 不写 approval_record
     *  - 触发 InitiationResubmittedEvent → 邮件通知当前审批人
     */
    @Transactional
    public ProjectInitiation resubmit(Long initiationId, Long applicantId) {
        ProjectInitiation i = get(initiationId);
        i.getStatus().getCode();
        if (!"SUPPLEMENT".equals(i.getStatus().getCode())) {
            throw new BusinessException("Only SUPPLEMENT status can be resubmitted, current: " + i.getStatus().getCode());
        }
        if (i.getCurrentStep() == null || i.getCurrentStep().isBlank()) {
            throw new BusinessException("Resubmit requires currentStep, found null/blank");
        }
        if (!java.util.Objects.equals(i.getApplicantId(), applicantId)) {
            throw new BusinessException("Only the applicant can resubmit, current applicantId=" + i.getApplicantId());
        }

        // WP-M7-05: 引擎实例 SUPPLEMENT 后 cancel + 重开一个
        // 保持业务状态 PENDING + currentStep 不变 (重走同一 step)
        if (i.getApprovalInstanceId() != null) {
            try {
                // 1) 取消旧实例 (仅申请人可 cancel)
                approvalEngine.cancel(i.getApprovalInstanceId(), applicantId);
                // 2) 创建新实例 (同 bizId, kind=init, flow=STANDARD_INITIATION)
                ApprovalFlowInstance newInst = approvalEngine.start(
                    "init", "STANDARD_INITIATION",
                    i.getId(), i.getCode(),
                    i.getApplicantId(), i.getDepartmentId(),
                    i.getContractAmount() != null ? "{\"amount\":" + i.getContractAmount().toPlainString() + "}" : null);
                i.setApprovalInstanceId(newInst.getId());
            } catch (Exception e) {
                throw new BusinessException("引擎重提失败: " + e.getMessage());
            }
        }

        i.setStatus(statusRepo.findAll().stream()
                .filter(s -> "PENDING".equals(s.getCode())).findFirst()
                .orElseThrow(() -> new BusinessException("Status PENDING not seeded")));

        // 发布事件(发邮件 + 写 UNREAD 通知)
        AppUser applicant = userRepository.findById(i.getApplicantId()).orElse(null);
        String stepName = stepRepo.findAll().stream()
                .filter(s -> s.getCode().equals(i.getCurrentStep()))
                .map(ApprovalStep::getName).findFirst().orElse(i.getCurrentStep());
        Long currentStepUserId = findStepUserId(i.getCurrentStep(), i.getDepartmentId()).userId();
        eventPublisher.publishEvent(new InitiationResubmittedEvent(
                i.getId(), i.getCode(), i.getTitle(),
                applicant == null ? null : applicant.getId(),
                applicant == null ? "Unknown" : applicant.getFullName(),
                applicant == null ? null : applicant.getEmail(),
                i.getCurrentStep(), stepName, currentStepUserId,
                Instant.now()
        ));
        return i;
    }

    @Transactional
    public ProjectInitiation submit(ProjectInitiation i) {
        if (i.getCode() == null || i.getCode().isBlank()) {
            i.setCode("IR-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (initiationRepository.existsByCodeAndDeletedFalse(i.getCode())) {
            throw new BusinessException("Initiation code exists: " + i.getCode());
        }
        i.setId(null);
        i.setStatus(statusRepo.findAll().stream()
                .filter(s -> "PENDING".equals(s.getCode())).findFirst()
                .orElseThrow(() -> new BusinessException("Status PENDING not seeded")));
        i.setCurrentStep(APPROVAL_FLOW.get(0));
        i.setSubmittedAt(Instant.now());
        ProjectInitiation saved = initiationRepository.save(i);

        // 发布提交事件 → 通知部门负责人
        AppUser applicant = userRepository.findById(saved.getApplicantId()).orElse(null);
        Long deptLeadId = findStepUserId(saved.getCurrentStep(), saved.getDepartmentId()).userId();

        // WP-M7-03: 同步启动通用审批流实例 (kind="init", flowCode="STANDARD_INITIATION")
        // 引擎实例创建后, ApprovalStepActivatedEvent 会被 InitiationApprovalBridgeListener
        // 转发为 InitiationSubmittedEvent (避免重复发邮件)
        try {
            Long instId = approvalAdapter.startInitiation(saved);
            if (instId != null) {
                saved.setApprovalInstanceId(instId);
            }
        } catch (Exception e) {
            log.error("[Initiation] 启动通用审批流失败 (但不阻断提交) initiationId={} code={} err={}",
                saved.getId(), saved.getCode(), e.getMessage());
        }

        // 老路径仍发 InitiationSubmittedEvent (因为 BridgeListener 会再次发同一个事件, 需要去重)
        // 临时方案: 仅当 BridgeListener 未启用时发 (V6.0 之前的行为)
        // 当前 BridgeListener 已启用, 此处不再 publish
        // (此行历史: 通知中心通过 InitiationSubmittedEvent 触发, 现有 Bridge 转发会接管)
        return saved;
    }

    public record InitiationApprovalDecision(String decision, String comment) {} // APPROVED/REJECTED/SUPPLEMENT

    @Transactional
    public ProjectInitiation decide(Long initiationId, Long approverId, InitiationApprovalDecision d) {
        ProjectInitiation i = get(initiationId);
        if (i.getStatus().isTerminal()) {
            throw new BusinessException("Initiation is already in terminal status: " + i.getStatus().getCode());
        }
        int idx = APPROVAL_FLOW.indexOf(i.getCurrentStep());
        if (idx < 0) throw new BusinessException("Bad current step: " + i.getCurrentStep());

        ApprovalStep step = stepRepo.findAll().stream()
                .filter(s -> s.getCode().equals(i.getCurrentStep()))
                .findFirst().orElseThrow(() -> new BusinessException("Step not found"));

        // WP-M7-07: ApprovalRecord 双写已废弃 (决策由 ApprovalFlowAction 单一事实源记录)
        // 保留代审推断日志 (审计: 实际是 backup 代审的, 应在引擎记录中标记)
        Long onBehalfOfUserId = inferOnBehalfOfUserId(i.getCurrentStep(), i.getDepartmentId(), approverId);
        if (onBehalfOfUserId != null) {
            log.info("[BackupApprover] 代审记录: 立项 {} 由 actorId={} 代主审批人={}", initiationId, approverId, onBehalfOfUserId);
        }

        // WP-M7-06: decide 委托 ApprovalEngine (instanceId != null)
        // 引擎返回 ApprovalStatus 后,业务根据状态回写 ProjectInitiation
        // 老数据 (instanceId=null) 走老路径,业务行为不变
        String nextStepCode = null;
        String nextStepName = null;
        com.hex.projectgovern.module.approval.ApprovalFlowInstance engineInst = null;
        if (i.getApprovalInstanceId() != null) {
            try {
                com.hex.projectgovern.module.approval.ApprovalDecision engineDecision =
                    com.hex.projectgovern.module.approval.ApprovalDecision.valueOf(d.decision());
                engineInst = approvalEngine.decide(i.getApprovalInstanceId(), approverId, engineDecision, d.comment());
            } catch (Exception e) {
                throw new BusinessException("引擎推进失败: " + e.getMessage());
            }
        } else {
            log.warn("[Initiation] 立项 {} 缺 approval_instance_id,走老路径", initiationId);
        }

        if (engineInst != null) {
            // 引擎 1-based stepNo → 业务 step code (0 表示已终止)
            String engineStepCode = engineInst.getCurrentStepNo() != null && engineInst.getCurrentStepNo() > 0
                ? APPROVAL_FLOW.get(Math.min(engineInst.getCurrentStepNo() - 1, APPROVAL_FLOW.size() - 1))
                : null;

            switch (engineInst.getStatus()) {
                case APPROVED -> {
                    // 终态判断: 必须 3 step 都通过才到 EXEC_APPROVED
                    if ("EXEC".equals(engineStepCode)) {
                        i.setStatus(statusRepo.findAll().stream()
                                .filter(s -> "EXEC_APPROVED".equals(s.getCode())).findFirst().orElseThrow());
                        i.setCurrentStep(null);
                        i.setClosedAt(Instant.now());
                        createProjectFromInitiation(i);
                        applyLatestAiDraft(i.getId(), approverId);
                    } else {
                        // 引擎认为 APPROVED 但 currentStep 还在前面 step → 实际是 PENDING
                        // (理论上不会发生, 因为 advance() 通过后再 approve 才到下一 step)
                        i.setCurrentStep(engineStepCode);
                        String nextCode = switch (engineStepCode) {
                            case "DEPT_LEAD" -> "DEPT_APPROVED";
                            case "PMO_ADMIN" -> "PMO_APPROVED";
                            default -> i.getStatus().getCode();
                        };
                        i.setStatus(statusRepo.findAll().stream()
                                .filter(s -> s.getCode().equals(nextCode)).findFirst()
                                .orElseThrow(() -> new BusinessException("Status not found: " + nextCode)));
                        nextStepCode = engineStepCode;
                        nextStepName = step.getName();
                    }
                }
                case REJECTED -> {
                    i.setStatus(statusRepo.findAll().stream()
                            .filter(s -> "REJECTED".equals(s.getCode())).findFirst().orElseThrow());
                    i.setCurrentStep(null);
                    i.setClosedAt(Instant.now());
                }
                case SUPPLEMENT -> {
                    i.setStatus(statusRepo.findAll().stream()
                            .filter(s -> "SUPPLEMENT".equals(s.getCode())).findFirst().orElseThrow());
                    // 留在当前步骤,等申请人补材料后重新提交
                    nextStepCode = i.getCurrentStep();
                    nextStepName = step.getName();
                }
                case PENDING -> {
                    // 推进到下一步
                    if (engineStepCode != null) {
                        i.setCurrentStep(engineStepCode);
                        String nextCode = switch (engineStepCode) {
                            case "DEPT_LEAD" -> "DEPT_APPROVED";
                            case "PMO_ADMIN" -> "PMO_APPROVED";
                            default -> i.getStatus().getCode();
                        };
                        i.setStatus(statusRepo.findAll().stream()
                                .filter(s -> s.getCode().equals(nextCode)).findFirst()
                                .orElseThrow(() -> new BusinessException("Status not found: " + nextCode)));
                        nextStepCode = engineStepCode;
                        nextStepName = stepRepo.findAll().stream()
                                .filter(s -> s.getCode().equals(engineStepCode))
                                .map(ApprovalStep::getName).findFirst().orElse(engineStepCode);
                    }
                }
                default -> {
                    log.warn("[Initiation] 引擎返回未预期状态 {} for initiationId={}", engineInst.getStatus(), initiationId);
                }
            }
        } else {
            // 老路径(approvalInstanceId=null):完全保留原始逻辑
            switch (d.decision()) {
                case "REJECTED" -> {
                    i.setStatus(statusRepo.findAll().stream()
                            .filter(s -> "REJECTED".equals(s.getCode())).findFirst().orElseThrow());
                    i.setCurrentStep(null);
                    i.setClosedAt(Instant.now());
                }
                case "SUPPLEMENT" -> {
                    i.setStatus(statusRepo.findAll().stream()
                            .filter(s -> "SUPPLEMENT".equals(s.getCode())).findFirst().orElseThrow());
                    nextStepCode = i.getCurrentStep();
                    nextStepName = step.getName();
                }
                case "APPROVED" -> {
                    if (idx + 1 >= APPROVAL_FLOW.size()) {
                        i.setStatus(statusRepo.findAll().stream()
                                .filter(s -> "EXEC_APPROVED".equals(s.getCode())).findFirst().orElseThrow());
                        i.setCurrentStep(null);
                        i.setClosedAt(Instant.now());
                        createProjectFromInitiation(i);
                        applyLatestAiDraft(i.getId(), approverId);
                    } else {
                        String next = APPROVAL_FLOW.get(idx + 1);
                        i.setCurrentStep(next);
                        String nextCode = switch (next) {
                            case "DEPT_LEAD" -> "DEPT_APPROVED";
                            case "PMO_ADMIN" -> "PMO_APPROVED";
                            default -> i.getStatus().getCode();
                        };
                        i.setStatus(statusRepo.findAll().stream()
                                .filter(s -> s.getCode().equals(nextCode)).findFirst()
                                .orElseThrow(() -> new BusinessException("Status not found: " + nextCode)));
                        nextStepCode = next;
                        nextStepName = stepRepo.findAll().stream()
                                .filter(s -> s.getCode().equals(next))
                                .map(ApprovalStep::getName).findFirst().orElse(next);
                    }
                }
                default -> throw new BusinessException("Invalid decision: " + d.decision());
            }
        }

        // 发布审批决定事件 → 通知申请人 + 下一审批人
        AppUser applicant = userRepository.findById(i.getApplicantId()).orElse(null);
        AppUser approver = userRepository.findById(approverId).orElse(null);
        Long nextStepUserId = nextStepCode == null ? null : findStepUserId(nextStepCode, i.getDepartmentId()).userId();
        eventPublisher.publishEvent(new InitiationDecidedEvent(
                i.getId(), i.getCode(), i.getTitle(),
                applicant == null ? null : applicant.getId(),
                applicant == null ? "Unknown" : applicant.getFullName(),
                applicant == null ? null : applicant.getEmail(),
                approver == null ? null : approver.getId(),
                approver == null ? "Unknown" : approver.getFullName(),
                d.decision(), nextStepCode, nextStepName, nextStepUserId, d.comment(),
                Instant.now()
        ));
        return i;
    }

    private void createProjectFromInitiation(ProjectInitiation i) {
        if (i.getProjectId() != null) return;
        Project p = new Project();
        String newCode = i.getCode().replace("IR-", "P-AUTO-") + "-" + System.currentTimeMillis() % 10000;
        p.setCode(newCode);
        p.setName(i.getTitle());
        // V4.17: 项目类型按立项 projectTypeCode 找, 找不到才用第一个
        if (i.getProjectTypeCode() != null && !i.getProjectTypeCode().isBlank()) {
            typeRepo.findByCode(i.getProjectTypeCode()).ifPresentOrElse(
                    p::setType,
                    () -> { throw new BusinessException(400, "Unknown projectTypeCode: " + i.getProjectTypeCode()); }
            );
        } else {
            p.setType(typeRepo.findAll().get(0));
        }
        p.setStatus(projectStatusRepo.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getCode())).findFirst().orElseThrow());
        // V4.17: PM 优先用立项 pmUserId, 没有再回退到 applicantId
        p.setPmUserId(i.getPmUserId() != null ? i.getPmUserId() : i.getApplicantId());
        p.setDepartmentId(i.getDepartmentId());
        p.setBackground(i.getBackground());
        p.setGoals(i.getGoals());
        p.setScope(i.getScope());
        p.setPlanStartDate(i.getPlannedStart());
        p.setPlanEndDate(i.getPlannedEnd());
        p.setPlanWorkdays(i.getPlanWorkdays());
        p.setBudgetEstimate(i.getBudgetEstimate());
        // V4.17: 项目级别 / 预估毛利率 / 计划上线时间 同步
        p.setProjectLevelCode(i.getProjectLevelCode());
        p.setExpectedGrossMarginPct(i.getExpectedGrossMarginPct());
        p.setPlannedLaunchDate(i.getPlannedLaunchDate());
        Project saved = projectRepository.save(p);
        i.setProjectId(saved.getId());
    }

    /**
     * EXEC 终审通过时自动调:把最新一份未应用的 AI 草稿 apply 到 wbs_task / milestone。
     * <p>取最新(按 createdAt desc)且 appliedAt IS NULL 的草稿;找不到/已 apply 则跳过,不报错。</p>
     */
    @Transactional
    public void applyLatestAiDraft(Long initiationId, Long actorId) {
        try {
            Optional<InitiationAiWbsDraft> latest = aiWbsDraftRepository
                    .findFirstByInitiationIdAndAppliedAtIsNullOrderByCreatedAtDesc(initiationId);
            if (latest.isEmpty()) {
                log.info("[ApplyLatestAiDraft] initiation={} has no pending AI draft, skip", initiationId);
                return;
            }
            Long draftId = latest.get().getId();
            var result = aiWbsService.applyDraft(draftId, actorId);
            log.info("[ApplyLatestAiDraft] initiation={} draft={} auto-applied: milestones={}, tasks={}",
                    initiationId, draftId, result.get("milestonesCreated"), result.get("tasksCreated"));
        } catch (Exception e) {
            // 自动 apply 失败不能阻塞审批流,只记日志
            log.error("[ApplyLatestAiDraft] failed for initiation={}: {}", initiationId, e.getMessage(), e);
        }
    }

    /**
     * 找 step 角色对应 user id: 部门内(DEPT_LEAD)→ 全局(PMO_ADMIN/EXEC)
     * 找不到返回 null(MailService 写 UNREAD 时会跳过)
     */
    /**
     * 解析步骤对应的审批人(主审批人)。
     * 触发 fallback 到 backup 的条件:enabled=false 或 deleted=true。
     * 返回 ApproverResolution 含 (effectiveUserId, onBehalfOfUserId)。
     *  - 本人审批:onBehalfOfUserId = null
     *  - 代审:effectiveUserId = backup.id, onBehalfOfUserId = 原主审批人.id
     *  - 主+备都不可用:返回 (null, null),调用方需走"无主审批人"分支
     */
        /**
     * 推断代审关系:给定 stepCode + 实际 actor(approverId),如果主审批人 != actor 且主审批人 disabled,
     * 返回主审批人 id(用于 audit on_behalf_of 记录);否则 null(本人审批)。
     */
    private Long inferOnBehalfOfUserId(String stepCode, Long departmentId, Long actorId) {
        if (stepCode == null || actorId == null) return null;
        String roleCode = switch (stepCode) {
            case "DEPT_LEAD" -> "DEPT_LEAD";
            case "PMO_ADMIN" -> "PMO_ADMIN";
            case "EXEC"      -> "EXEC";
            default -> null;
        };
        if (roleCode == null) return null;
        AppUser primary = (roleCode.equals("DEPT_LEAD") && departmentId != null)
                ? userRepository.findFirstByDepartmentIdAndPrimaryRoleCodeAndDeletedFalse(departmentId, roleCode).orElse(null)
                : userRepository.findFirstByPrimaryRoleCodeAndDeletedFalse(roleCode).orElse(null);
        if (primary == null) return null;
        if (primary.getId().equals(actorId)) return null;       // 本人审批
        if (primary.isEnabled()) return null;                   // 主审批人未禁用,actor 是别人(权限问题,非代审)
        return primary.getId();                                  // 代审,记录原主审批人
    }

    private ApproverResolution findStepUserId(String stepCode, Long departmentId) {
        if (stepCode == null) return new ApproverResolution(null, null);
        String roleCode = switch (stepCode) {
            case "DEPT_LEAD" -> "DEPT_LEAD";
            case "PMO_ADMIN" -> "PMO_ADMIN";
            case "EXEC"      -> "EXEC";
            default -> null;
        };
        if (roleCode == null) return new ApproverResolution(null, null);

        AppUser primary = (roleCode.equals("DEPT_LEAD") && departmentId != null)
                ? userRepository.findFirstByDepartmentIdAndPrimaryRoleCodeAndDeletedFalse(departmentId, roleCode).orElse(null)
                : userRepository.findFirstByPrimaryRoleCodeAndDeletedFalse(roleCode).orElse(null);

        if (primary == null) return new ApproverResolution(null, null);
        if (primary.isEnabled()) {
            return new ApproverResolution(primary.getId(), null);
        }
        // 主审批人 disabled — fallback 到 backup
        if (primary.getBackupUserId() == null) {
            log.warn("[BackupApprover] 主审批人 {} ({}) 已禁用但无 backup 配置,fallback 失败",
                    primary.getUsername(), primary.getId());
            return new ApproverResolution(null, null);
        }
        AppUser backup = userRepository.findByIdAndDeletedFalse(primary.getBackupUserId()).orElse(null);
        if (backup == null || !backup.isEnabled()) {
            log.warn("[BackupApprover] 备选审批人 {} 不可用(deleted/disabled),fallback 失败", primary.getBackupUserId());
            return new ApproverResolution(null, null);
        }
        log.info("[BackupApprover] 主审批人 {} disabled → 由 {} 代审 (on_behalf_of={})",
                primary.getUsername(), backup.getUsername(), primary.getId());
        return new ApproverResolution(backup.getId(), primary.getId());
    }

    // =================================================================
    // V4.13 SOW 贴文本支持(Step 2 第二种 SOW 来源,与 SOW 文件互为补充)
    // =================================================================

    /**
     * 更新 SOW 贴文本。自动 trim + 截断 50KB;若非空且本立项下无 SOW 文件,
     * 自动置 sowReceived=true(等同上传一个 SOW 文件)。
     *
     * @return Map{id, sowPasteText, sowPasteLength, sowReceived, hasSowFiles}
     */
    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Object> updateSowPaste(Long initiationId, String sowPasteText, Long actorId) {
        ProjectInitiation i = get(initiationId);
        String trimmed = sowPasteText == null ? null : sowPasteText.trim();
        boolean truncated = false;
        if (trimmed != null && trimmed.length() > SOW_PASTE_MAX) {
            trimmed = trimmed.substring(0, SOW_PASTE_MAX);
            truncated = true;
        }
        i.setSowPasteText(trimmed);
        // 重新评估 sowReceived:贴文本非空 OR 有 SOW 文件 → true
        boolean hasFiles = !sowFileRepository
                .findByInitiationIdAndDeletedFalseOrderByUploadedAtDesc(initiationId).isEmpty();
        boolean hasPaste = trimmed != null && !trimmed.isBlank();
        i.setSowReceived(hasFiles || hasPaste);
        initiationRepository.save(i);
        log.info("[SowPaste] initiation={} pasteLen={} truncated={} hasFiles={} hasPaste={} sowReceived={} by={}",
                initiationId, trimmed == null ? 0 : trimmed.length(), truncated, hasFiles, hasPaste,
                i.isSowReceived(), actorId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", i.getId());
        result.put("sowPasteText", trimmed);
        result.put("sowPasteLength", trimmed == null ? 0 : trimmed.length());
        result.put("truncated", truncated);
        result.put("sowReceived", i.isSowReceived());
        result.put("hasSowFiles", hasFiles);
        return result;
    }

    // =================================================================
    // V4.19 增量更新立项字段 (合同金额等)
    // =================================================================

    /**
     * 增量更新立项字段 — 仅更新 body 里非 null 的字段,允许白名单字段。
     * <p>白名单:contractAmount, contractCurrency, planWorkWeeks, background, goals, scope,
     *          clientName, clientContactName, clientContactPhone, title, arUserName, srUserName, frUserName
     * <p>不允许通过此接口修改:code/status/currentStep/applicantId 等流转字段。
     */
    @org.springframework.transaction.annotation.Transactional
    public ProjectInitiation updateFields(Long initiationId,
                                          java.util.Map<String, Object> patch,
                                          Long actorId) {
        // ⚠️ 强制 JOIN FETCH status 防止返回时 LAZY 失败
        ProjectInitiation i = initiationRepository.findActiveById(initiationId)
                .orElseThrow(() -> new com.hex.projectgovern.common.exception.BusinessException(
                        404, "Initiation not found: " + initiationId));
        java.util.Set<String> allowed = java.util.Set.of(
                "contractAmount", "contractCurrency", "planWorkWeeks",
                "background", "goals", "scope",
                "clientName", "clientContactName", "clientContactPhone",
                "title",
                // V4.17: 立项基础信息补全 — 部门 / PM / 类型 / 级别 / 毛利率 / 入场 / 上线 / 结束
                "departmentId", "pmUserId",
                "projectTypeCode", "projectLevelCode",
                "expectedGrossMarginPct",
                "plannedStart", "plannedEnd", "plannedLaunchDate"
        );
        for (var e : patch.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            if (val == null) continue;
            if (!allowed.contains(key)) {
                log.warn("[updateFields] 忽略非白名单字段: {} = {}", key, val);
                continue;
            }
            switch (key) {
                case "contractAmount" -> i.setContractAmount(new java.math.BigDecimal(val.toString()));
                case "contractCurrency" -> i.setContractCurrency(val.toString());
                case "planWorkWeeks" -> i.setPlanWorkWeeks(((Number) val).intValue());
                case "background", "goals", "scope" -> {
                    // ProjectInitiation 字段名对应 setBackground/setGoals/setScope
                    try {
                        var setter = i.getClass().getMethod("set" + Character.toUpperCase(key.charAt(0)) + key.substring(1), String.class);
                        setter.invoke(i, val.toString());
                    } catch (Exception ex) {
                        log.warn("[updateFields] setter 失败 {}: {}", key, ex.getMessage());
                    }
                }
                case "clientName" -> i.setClientName(val.toString());
                case "clientContactName" -> i.setClientContactName(val.toString());
                case "clientContactPhone" -> i.setClientContactPhone(val.toString());
                case "title" -> i.setTitle(val.toString());
                // V4.17: 基础信息
                case "departmentId" -> i.setDepartmentId(((Number) val).longValue());
                case "pmUserId" -> i.setPmUserId(((Number) val).longValue());
                case "projectTypeCode" -> i.setProjectTypeCode(val.toString());
                case "projectLevelCode" -> i.setProjectLevelCode(val.toString());
                case "expectedGrossMarginPct" -> i.setExpectedGrossMarginPct(new java.math.BigDecimal(val.toString()));
                case "plannedStart" -> i.setPlannedStart(LocalDate.parse(val.toString()));
                case "plannedEnd" -> i.setPlannedEnd(LocalDate.parse(val.toString()));
                case "plannedLaunchDate" -> i.setPlannedLaunchDate(LocalDate.parse(val.toString()));
            }
            log.info("[updateFields] initiation={} field={} value={} by={}", initiationId, key, val, actorId);
        }
        return initiationRepository.save(i);
    }
}
