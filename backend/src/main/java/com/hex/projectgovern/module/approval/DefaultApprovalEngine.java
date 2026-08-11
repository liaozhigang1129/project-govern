package com.hex.projectgovern.module.approval;

import com.hex.projectgovern.module.approval.event.ApprovalStepActivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认审批引擎实现 (DB 配置驱动)
 *
 * <p>核心循环:
 * <ol>
 *   <li>start → 写实例(status=INITIAL) → 推进到 step 1 → 写 STARTED 动作 → 发 ApprovalStepActivatedEvent
 *   <li>decide → 校验当前 step + 决策合法 → 写动作 → 推进/终止
 *   <li>cancel → 校验非终态 → 置 CANCELLED
 * </ol>
 *
 * <p>审批人解析委派给 {@link ApproverResolver} (注入),
 * 跳过条件委派给 {@link SkipConditionEvaluator}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultApprovalEngine implements ApprovalEngine {

    private final ApprovalFlowDefRepository defRepo;
    private final ApprovalFlowStepRepository stepRepo;
    private final ApprovalFlowInstanceRepository instanceRepo;
    private final ApprovalFlowActionRepository actionRepo;
    private final ApproverResolver approverResolver;
    private final SkipConditionEvaluator skipEvaluator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ApprovalFlowInstance start(String kind, String flowCode, Long bizId, String bizCode,
                                       Long applicantId, Long departmentId, String bizPayload) {
        // 1. 幂等检查
        instanceRepo.findByKindAndBizId(kind, bizId).ifPresent(existing -> {
            throw new ApprovalException(
                "审批流已存在: kind=" + kind + " bizId=" + bizId + " instanceId=" + existing.getId()
                + " status=" + existing.getStatus());
        });

        // 2. 取流程定义 (取最新 enabled version)
        ApprovalFlowDef def = defRepo.findLatestEnabled(kind).orElseThrow(() ->
            new ApprovalException("未找到启用流程: kind=" + kind));

        if (!def.getCode().equals(flowCode)) {
            // 同 kind 可有不同流程编码(目前只一种,但预留)
            throw new ApprovalException("流程编码不匹配: def=" + def.getCode() + " req=" + flowCode);
        }

        // 3. 取步骤
        List<ApprovalFlowStep> steps = stepRepo.findByFlowDefIdOrderByStepNoAsc(def.getId());
        if (steps.isEmpty()) {
            throw new ApprovalException("流程无步骤: def=" + def.getId());
        }

        // 4. 创建实例 (status=INITIAL, currentStepNo=0 表示还未推进)
        ApprovalFlowInstance inst = new ApprovalFlowInstance();
        inst.setFlowDefId(def.getId());
        inst.setKind(kind);
        inst.setBizId(bizId);
        inst.setBizCode(bizCode);
        inst.setApplicantId(applicantId);
        inst.setDepartmentId(departmentId);
        inst.setBizPayload(bizPayload);
        inst.setStatus(ApprovalStatus.INITIAL);
        inst.setCurrentStepNo(0);
        inst = instanceRepo.save(inst);

        // 5. 写 STARTED 动作 (审计)
        ApprovalFlowAction startAction = new ApprovalFlowAction();
        startAction.setInstanceId(inst.getId());
        startAction.setStepNo(0);
        startAction.setDecision(ApprovalDecision.STARTED);
        startAction.setComment("流程启动 flowCode=" + flowCode);
        actionRepo.save(startAction);

        log.info("[ApprovalEngine] 流程启动 instance={} kind={} bizId={} flowCode={} → step 1",
            inst.getId(), kind, bizId, flowCode);

        // 6. 推进到 step 1: 评估 skip_when / 解析审批人 / 发事件
        advance(inst, steps, true);
        return inst;
    }

    @Override
    @Transactional
    public ApprovalFlowInstance decide(Long instanceId, Long approverId, ApprovalDecision decision, String comment) {
        if (!decision.isManual()) {
            throw new ApprovalException("非手动决策不可调用 decide(): " + decision);
        }

        ApprovalFlowInstance inst = instanceRepo.findById(instanceId).orElseThrow(() ->
            new ApprovalException("流程实例不存在: " + instanceId));

        if (inst.getStatus().isTerminal()) {
            throw new ApprovalException("流程已终态: " + inst.getStatus());
        }
        if (inst.getStatus() != ApprovalStatus.PENDING) {
            throw new ApprovalException("流程不在 PENDING 状态: " + inst.getStatus());
        }

        // 1. 校验当前 step 审批人 (optional 启发式: 不是严格校验,允许 backup 代审)
        Long expectedApprover = resolveCurrentStepApprover(instanceId);
        if (expectedApprover != null && !expectedApprover.equals(approverId)) {
            log.info("[ApprovalEngine] 代审检测: expected={} actual={}", expectedApprover, approverId);
        }

        // 2. 写决策
        ApprovalFlowAction action = new ApprovalFlowAction();
        action.setInstanceId(inst.getId());
        action.setStepNo(inst.getCurrentStepNo());
        action.setApproverId(approverId);
        action.setDecision(decision);
        action.setComment(comment);
        // 代审关系由调用方传入并写入 on_behalf_of_user_id
        // (InitiationService 决定,引擎不做推断)
        actionRepo.save(action);

        // 3. 根据决策推进
        switch (decision) {
            case APPROVED -> {
                List<ApprovalFlowStep> steps = stepRepo.findByFlowDefIdOrderByStepNoAsc(inst.getFlowDefId());
                advance(inst, steps, false);
            }
            case REJECTED -> {
                inst.setStatus(ApprovalStatus.REJECTED);
                inst.setFinishedAt(Instant.now());
                instanceRepo.save(inst);
                log.info("[ApprovalEngine] 流程拒绝 instance={} step={}", inst.getId(), inst.getCurrentStepNo());
            }
            case SUPPLEMENT -> {
                inst.setStatus(ApprovalStatus.SUPPLEMENT);
                inst.setFinishedAt(Instant.now());
                instanceRepo.save(inst);
                log.info("[ApprovalEngine] 流程补充材料 instance={} step={}", inst.getId(), inst.getCurrentStepNo());
            }
            default -> throw new ApprovalException("未实现的决策类型: " + decision);
        }
        return inst;
    }

    @Override
    @Transactional
    public ApprovalFlowInstance cancel(Long instanceId, Long applicantId) {
        ApprovalFlowInstance inst = instanceRepo.findById(instanceId).orElseThrow(() ->
            new ApprovalException("流程实例不存在: " + instanceId));
        if (!inst.getApplicantId().equals(applicantId)) {
            throw new ApprovalException("仅申请人可撤回: applicant=" + inst.getApplicantId() + " actor=" + applicantId);
        }
        if (inst.getStatus().isTerminal()) {
            throw new ApprovalException("流程已终态,不可撤回: " + inst.getStatus());
        }
        inst.setStatus(ApprovalStatus.CANCELLED);
        inst.setFinishedAt(Instant.now());
        instanceRepo.save(inst);

        ApprovalFlowAction action = new ApprovalFlowAction();
        action.setInstanceId(inst.getId());
        action.setStepNo(inst.getCurrentStepNo());
        action.setDecision(ApprovalDecision.STARTED);  // 复用 audit 枚举,字�� comment 标注 CANCELLED
        action.setComment("流程撤回 by applicant=" + applicantId);
        actionRepo.save(action);

        log.info("[ApprovalEngine] 流程撤回 instance={}", inst.getId());
        return inst;
    }

    @Override
    public ApprovalFlowInstance findByBiz(String kind, Long bizId) {
        return instanceRepo.findByKindAndBizId(kind, bizId).orElse(null);
    }

    @Override
    public Long resolveCurrentStepApprover(Long instanceId) {
        ApprovalFlowInstance inst = instanceRepo.findById(instanceId).orElseThrow(() ->
            new ApprovalException("流程实例不存在: " + instanceId));
        ApprovalFlowStep step = stepRepo.findByFlowDefIdOrderByStepNoAsc(inst.getFlowDefId()).stream()
            .filter(s -> s.getStepNo().equals(inst.getCurrentStepNo()))
            .findFirst().orElseThrow(() ->
                new ApprovalException("当前 step 不存在: instance=" + instanceId + " step=" + inst.getCurrentStepNo()));
        return approverResolver.resolve(step.getRoleCode(), inst.getDepartmentId(), inst.getApplicantId());
    }

    @Override
    public boolean isTerminal(Long instanceId) {
        return instanceRepo.findById(instanceId).map(i -> i.getStatus().isTerminal()).orElse(true);
    }

    @Override
    public Map<String, Object> describe(Long instanceId) {
        ApprovalFlowInstance inst = instanceRepo.findById(instanceId).orElseThrow(() ->
            new ApprovalException("流程实例不存在: " + instanceId));
        List<ApprovalFlowStep> steps = stepRepo.findByFlowDefIdOrderByStepNoAsc(inst.getFlowDefId());
        ApprovalFlowStep curStep = steps.stream()
            .filter(s -> s.getStepNo().equals(inst.getCurrentStepNo()))
            .findFirst().orElse(null);
        List<ApprovalFlowAction> actions = actionRepo.findByInstanceIdOrderByDecidedAtAsc(inst.getId());

        Map<String, Object> r = new HashMap<>();
        r.put("instanceId", inst.getId());
        r.put("kind", inst.getKind());
        r.put("bizId", inst.getBizId());
        r.put("bizCode", inst.getBizCode());
        r.put("status", inst.getStatus());
        r.put("currentStepNo", inst.getCurrentStepNo());
        r.put("currentStepName", curStep != null ? curStep.getName() : null);
        r.put("currentApprover", resolveCurrentStepApprover(instanceId));
        r.put("actions", actions);
        r.put("createdAt", inst.getCreatedAt());
        r.put("finishedAt", inst.getFinishedAt());
        return r;
    }

    /**
     * 推进: 从当前 step+1 开始, 跳过被 skip 的 step, 找到下一个需要处理的 step 并发事件
     *
     * <p>语义约定:
     * <ul>
     *   <li>start() 调用时 inst.currentStepNo = steps[0].stepNo (= "已推进到 step 1" 的含义), 本方法从 curIdx 起检查 step 1 是否需 skip
     *   <li>decide(APPROVED) 调用时 inst.currentStepNo = 已通过 step, 本方法从 curIdx+1 找下一个
     * </ul>
     *
     * <p>识别 start vs decide 的方法: 看 start 动作是否已写。
     * 简化: 用 instance.status == INITIAL 表示 start 阶段
     */
    private void advance(ApprovalFlowInstance inst, List<ApprovalFlowStep> steps) {
        advance(inst, steps, inst.getStatus() == ApprovalStatus.INITIAL);
    }

    private void advance(ApprovalFlowInstance inst, List<ApprovalFlowStep> steps, boolean isStart) {
        // 重新拿最新 step (可能被前面代码更新过)
        steps = stepRepo.findByFlowDefIdOrderByStepNoAsc(inst.getFlowDefId());

        // start 阶段: 从 step 0 起检查 (currentStepNo=0)
        // decide 阶段: 从 curIdx+1 起找下一个 (currentStepNo=已过 step)
        int startIdx;
        if (isStart) {
            startIdx = 0;  // 直接从 step 0 开始
        } else {
            int curIdx = indexOfStep(steps, inst.getCurrentStepNo());
            if (curIdx < 0) {
                throw new ApprovalException("实例当前 step 不在流程定义: " + inst.getCurrentStepNo());
            }
            startIdx = curIdx + 1;
        }

        for (int i = startIdx; i < steps.size(); i++) {
            ApprovalFlowStep next = steps.get(i);
            boolean shouldSkip = skipEvaluator.shouldSkip(next.getSkipWhen(), inst.getBizPayload());
            if (shouldSkip) {
                ApprovalFlowAction skip = new ApprovalFlowAction();
                skip.setInstanceId(inst.getId());
                skip.setStepNo(next.getStepNo());
                skip.setDecision(ApprovalDecision.SKIPPED);
                skip.setComment("skip_when=" + next.getSkipWhen() + " 命中");
                actionRepo.save(skip);
                log.info("[ApprovalEngine] step 跳过 instance={} step={} reason={}",
                    inst.getId(), next.getStepNo(), next.getSkipWhen());
                continue;
            }

            // 推进到该 step
            inst.setCurrentStepNo(next.getStepNo());
            inst.setStatus(ApprovalStatus.PENDING);
            ApprovalFlowInstance saved = instanceRepo.save(inst);
            if (saved != null) inst = saved;

            // 解析审批人,��底 auto_approve_when
            Long approver = approverResolver.resolve(next.getRoleCode(), inst.getDepartmentId(), inst.getApplicantId());
            if (approver == null && Boolean.TRUE.equals(next.getAutoApproveWhen())) {
                ApprovalFlowAction auto = new ApprovalFlowAction();
                auto.setInstanceId(inst.getId());
                auto.setStepNo(next.getStepNo());
                auto.setDecision(ApprovalDecision.SKIPPED);
                auto.setComment("无审批人 + auto_approve_when=true 自动通过");
                actionRepo.save(auto);
                log.info("[ApprovalEngine] step 自动通过 instance={} step={}", inst.getId(), next.getStepNo());
                continue;  // 不发事件, 进入下一轮检查
            }

            // 发事件: 当前 step 激活
            eventPublisher.publishEvent(new ApprovalStepActivatedEvent(
                inst.getId(), inst.getKind(), inst.getBizId(), inst.getBizCode(),
                inst.getApplicantId(), next.getStepNo(), next.getName(), next.getRoleCode(), approver,
                Instant.now()));
            return;  // 找到首个需推进的 step 后退出
        }
        // 全部 step 处理完 → APPROVED 终态
        inst.setStatus(ApprovalStatus.APPROVED);
        inst.setFinishedAt(Instant.now());
        instanceRepo.save(inst);
        instanceRepo.save(inst);
        log.info("[ApprovalEngine] 流程全部通过 instance={}", inst.getId());
    }

    /**
     * 本地 lambda 版 advance 用于测试可见性 (调试用, 生产代码用上面的)
     */
    public void advanceForTest(ApprovalFlowInstance inst, boolean isStart) {
        List<ApprovalFlowStep> steps = stepRepo.findByFlowDefIdOrderByStepNoAsc(inst.getFlowDefId());
        advance(inst, steps, isStart);
    }

    private int indexOfStep(List<ApprovalFlowStep> steps, int stepNo) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getStepNo() == stepNo) return i;
        }
        return -1;
    }
}