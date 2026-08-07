package com.company.pmo.module.risk;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.milestone.Milestone;
import com.company.pmo.module.milestone.MilestoneRepository;
import com.company.pmo.module.org.AppUser;
import com.company.pmo.module.org.UserRepository;
import com.company.pmo.module.project.ProjectRepository;
import com.company.pmo.module.risk.dto.*;
import com.company.pmo.module.risk.dto.RiskResponse;
import com.company.pmo.module.wbs.WbsTask;
import com.company.pmo.module.wbs.WbsTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P4 风险管理 — 业务逻辑层。
 *
 * <h3>关键设计</h3>
 * <ul>
 *   <li>score / level 完全由 probability × impact 推导, 不接受客户端传入, 避免脏数据</li>
 *   <li>每次 save 都会写一条 RiskHistory (CREATED / SCORE_CHANGED / STATUS_CHANGED 等)</li>
 *   <li>批量组装 owner / wbsTask / milestone 名字, 1 次 IN 查询, 避免 N+1</li>
 *   <li>softDelete 改 deleted=true + 写 history</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskRepository riskRepository;
    private final RiskResponseRepository responseRepository;
    private final RiskHistoryRepository historyRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WbsTaskRepository wbsTaskRepository;
    private final MilestoneRepository milestoneRepository;

    // ============================================================
    // 风险 CRUD
    // ============================================================

    @Transactional(readOnly = true)
    public List<RiskResponse> listByProject(Long projectId) {
        validateProject(projectId);
        List<Risk> all = riskRepository.findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(projectId);
        return enrich(all);
    }

    @Transactional(readOnly = true)
    public List<RiskResponse> listActiveByProject(Long projectId) {
        validateProject(projectId);
        List<Risk> active = riskRepository.findActiveByProject(projectId);
        return enrich(active);
    }

    @Transactional(readOnly = true)
    public RiskResponse getById(Long id) {
        Risk r = riskRepository.findActiveById(id)
                .orElseThrow(() -> new BusinessException(404, "Risk not found: " + id));
        return enrichOne(r);
    }

    /**
     * 新建 / 更新 (id=null 新建, id!=null 更新)。
     * 写完同步插一条 history (CREATED 或 STATUS_CHANGED / SCORE_CHANGED)。
     */
    @Transactional
    public RiskResponse save(RiskRequest req, Long operatorId) {
        validateProject(req.projectId());

        Risk r = (req.id() == null)
                ? new Risk()
                : riskRepository.findActiveById(req.id())
                    .orElseThrow(() -> new BusinessException(404, "Risk not found: " + req.id()));

        if (req.id() != null && !r.getProjectId().equals(req.projectId())) {
            throw new BusinessException("不能把风险改到别的项目下");
        }

        // code 唯一性
        if (req.id() == null) {
            if (riskRepository.countByProjectIdAndCodeAndDeletedFalse(req.projectId(), req.code()) > 0) {
                throw new BusinessException("风险编号已存在: " + req.code());
            }
        } else {
            if (!req.code().equals(r.getCode())) {
                if (riskRepository.countByProjectIdAndCodeAndDeletedFalse(req.projectId(), req.code()) > 0) {
                    throw new BusinessException("风险编号已存在: " + req.code());
                }
            }
        }

        // 检测变化 (用于写 history)
        String oldStatus = r.getStatus();
        Integer oldScore = r.getScore();
        String oldLevel = r.getLevel();
        Long oldOwner = r.getOwnerUserId();

        r.setProjectId(req.projectId());
        r.setCode(req.code());
        r.setTitle(req.title());
        r.setDescription(req.description());
        r.setCategory(req.category());
        r.setProbability(req.probability());
        r.setImpact(req.impact());
        // 关键: score + level 自动算
        r.recomputeScoreAndLevel();
        r.setStatus(req.statusOrDefault());
        r.setOwnerUserId(req.ownerUserId());
        r.setMitigation(req.mitigation());
        r.setContingency(req.contingency());
        r.setResponseStrategy(req.responseStrategy());
        if (req.identifiedDate() != null) r.setIdentifiedDate(req.identifiedDate());
        r.setTargetCloseDate(req.targetCloseDate());
        r.setRelatedWbsTaskId(req.relatedWbsTaskId());
        r.setRelatedMilestoneId(req.relatedMilestoneId());

        // CLOSED 状态自动写 actualCloseDate
        if ("CLOSED".equals(r.getStatus()) && r.getActualCloseDate() == null) {
            r.setActualCloseDate(LocalDate.now());
        }

        Risk saved = riskRepository.save(r);

        // 写 history
        if (req.id() == null) {
            appendHistory(saved.getId(), "CREATED", null, null, null, "风险登记", operatorId);
        } else {
            if (!Objects.equals(oldStatus, saved.getStatus())) {
                appendHistory(saved.getId(), "STATUS_CHANGED", "status", oldStatus, saved.getStatus(), null, operatorId);
            }
            if (!Objects.equals(oldScore, saved.getScore())) {
                appendHistory(saved.getId(), "SCORE_CHANGED", "score",
                        String.valueOf(oldScore), String.valueOf(saved.getScore()), null, operatorId);
                if (!Objects.equals(oldLevel, saved.getLevel())) {
                    appendHistory(saved.getId(), "LEVEL_CHANGED", "level",
                            oldLevel, saved.getLevel(), null, operatorId);
                }
            }
            if (!Objects.equals(oldOwner, saved.getOwnerUserId())) {
                appendHistory(saved.getId(), "OWNER_CHANGED", "owner_user_id",
                        String.valueOf(oldOwner), String.valueOf(saved.getOwnerUserId()), null, operatorId);
            }
        }

        return enrichOne(saved);
    }

    @Transactional
    public void softDelete(Long id, Long operatorId) {
        Risk r = riskRepository.findActiveById(id)
                .orElseThrow(() -> new BusinessException(404, "Risk not found: " + id));
        r.setDeleted(true);
        riskRepository.save(r);
        // Bug fix #1: 之前用 STATUS_CHANGED + fieldName=deleted 跟"status 字段变了"语义冲突,
        // 前端时间轴会误显示成"风险状态变化"。改用 DELETED 动作 + dedicated 字段,
        // 时间轴一眼能区分"软删"和"状态切换"。
        appendHistory(id, "DELETED", "deleted", "false", "true", "软删除风险", operatorId);
    }

    // ============================================================
    // 应对行动 (RiskResponse)
    // ============================================================

    @Transactional(readOnly = true)
    public List<RiskResponseDto.Item> listResponses(Long riskId) {
        return responseRepository.findByRiskIdAndDeletedFalseOrderByIdAsc(riskId).stream()
                .map(this::toResponseItem)
                .toList();
    }

    @Transactional
    public RiskResponseDto.Item upsertResponse(Long riskId, RiskResponseDto.Request req, Long operatorId) {
        Risk r = riskRepository.findActiveById(riskId)
                .orElseThrow(() -> new BusinessException(404, "Risk not found: " + riskId));
        com.company.pmo.module.risk.RiskResponse resp = (req.id() == null)
                ? new com.company.pmo.module.risk.RiskResponse()
                : responseRepository.findById(req.id())
                    .orElseThrow(() -> new BusinessException(404, "Response not found: " + req.id()));
        if (req.id() != null && !resp.getRiskId().equals(riskId)) {
            throw new BusinessException("应对行动不属于该风险");
        }
        resp.setRiskId(riskId);
        resp.setAction(req.action());
        resp.setOwnerUserId(req.ownerUserId());
        resp.setDueDate(req.dueDate());
        resp.setStatus(req.statusOrDefault());
        resp.setNote(req.note());
        if ("DONE".equals(req.statusOrDefault()) && resp.getCompletedAt() == null) {
            resp.setCompletedAt(java.time.Instant.now());
        }
        com.company.pmo.module.risk.RiskResponse saved = responseRepository.save(resp);

        if (req.id() == null) {
            appendHistory(riskId, "RESPONSE_ADDED", null, null, null, req.action(), operatorId);
        } else if ("DONE".equals(req.statusOrDefault())) {
            appendHistory(riskId, "RESPONSE_DONE", "response", null, String.valueOf(saved.getId()), req.action(), operatorId);
        }
        return toResponseItem(saved);
    }

    @Transactional
    public void deleteResponse(Long responseId, Long operatorId) {
        com.company.pmo.module.risk.RiskResponse resp = responseRepository.findById(responseId)
                .orElseThrow(() -> new BusinessException(404, "Response not found: " + responseId));
        resp.setDeleted(true);
        responseRepository.save(resp);
        // Bug fix #2: 之前用 RESPONSE_DONE + fieldName=response_deleted 跟"应对行动完成"撞名,
        // 前端时间轴渲染时无法区分"完成"和"删除"。改用:
        //   action:   RESPONSE_DONE   (保留, 不动 DB CHECK 约束)
        //   fieldName: response.status
        //   oldValue: ACTIVE
        //   newValue: DELETED
        // 这样时间轴上"完成"和"删除"用同一 action 类, 但 value 明确表达状态变化。
        // 彻底解决需 V2.7 加 RESPONSE_REMOVED 枚举 (另议)。
        appendHistory(resp.getRiskId(), "RESPONSE_DONE", "response.status",
                "ACTIVE", "DELETED",
                "删除应对行动 #" + responseId, operatorId);
    }

    // ============================================================
    // 风险历史
    // ============================================================

    @Transactional(readOnly = true)
    public List<RiskHistoryItem> listHistory(Long riskId) {
        return historyRepository.findByRiskIdOrderByCreatedAtDescIdDesc(riskId).stream()
                .map(this::toHistoryItem)
                .toList();
    }

    // ============================================================
    // 健康度聚合
    // ============================================================

    @Transactional(readOnly = true)
    public RiskHealthSummary healthSummary(Long projectId) {
        validateProject(projectId);
        // JPQL 多列聚合查询, Hibernate 在 H2 上返回 Object[][] (外层 = 行, 内层 = 列),
        // PG 上是 Object[] of Object[]. 统一 unwrap 第一行。
        Object outer = riskRepository.aggregateHealth(projectId);
        Object[] row;
        if (outer instanceof Object[][] o2d) {
            row = o2d[0];
        } else if (outer instanceof Object[] o1d) {
            // PG: 第一个元素就是数字 (而非 Object[]), 但要小心: PG 上 COUNT() 返回 Object[]
            //     嵌套在 SELECT 多列里时, 顶层是 Object[] 直接 = [v0, v1, v2, ...]
            //     而非 Object[] of Object[]
            // 但是 mock 测试时, 如果传 new Object[]{ Long, Long, ... }, instanceof Object[] 也成立
            // 所以要判断 o1d[0] 是不是 Number
            if (o1d.length > 0 && o1d[0] instanceof Number) {
                row = o1d;
            } else {
                // 不是 Number 说明 o1d[0] 是另一层 Object[] (罕见 PG 行为)
                row = (Object[]) o1d[0];
            }
        } else {
            throw new IllegalStateException("Unexpected aggregateHealth return: " + outer);
        }
        // JPQL 单列 SELECT COUNT/MAX 在 H2/PG 都返回 Object[], 但元素类型:
        //   COUNT → Long (PG/H2 一致)
        //   MAX  → Integer (PG 默认) / Long (H2)
        //   COALESCE(... , 0) 整型字面量 → Integer (PG) / Long (H2)
        // 所以 maxScore 单独用 longValue 解
        long total      = ((Number) row[0]).longValue();
        long active     = ((Number) row[1]).longValue();
        long critical   = ((Number) row[2]).longValue();
        long high       = ((Number) row[3]).longValue();
        long occurred   = ((Number) row[4]).longValue();
        long maxScoreL  = ((Number) row[5]).longValue();
        int  maxScore   = (int) Math.min(maxScoreL, Integer.MAX_VALUE);

        List<Risk> all = riskRepository.findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(projectId);
        Map<String, Long> byCategory = all.stream()
                .filter(x -> !"CLOSED".equals(x.getStatus()) && !"ACCEPTED".equals(x.getStatus()))
                .collect(Collectors.groupingBy(Risk::getCategory, Collectors.counting()));
        Map<String, Long> byLevel = all.stream()
                .filter(x -> !"CLOSED".equals(x.getStatus()) && !"ACCEPTED".equals(x.getStatus()))
                .collect(Collectors.groupingBy(Risk::getLevel, Collectors.counting()));
        return new RiskHealthSummary(projectId, total, active, critical, high, occurred, maxScore, byCategory, byLevel);
    }

    /** 5x5 风险矩阵 */
    @Transactional(readOnly = true)
    public RiskMatrix.Matrix matrix(Long projectId) {
        validateProject(projectId);
        List<Risk> all = riskRepository.findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(projectId);
        List<RiskResponse> enriched = enrich(all);
        Map<String, List<RiskResponse>> buckets = enriched.stream()
                .collect(Collectors.groupingBy(
                        x -> x.probability() + "x" + x.impact(),
                        Collectors.toList()));
        List<RiskMatrix.Cell> cells = new ArrayList<>();
        for (int p = 1; p <= 5; p++) {
            for (int i = 1; i <= 5; i++) {
                String key = p + "x" + i;
                List<RiskResponse> list = buckets.getOrDefault(key, List.of());
                cells.add(new RiskMatrix.Cell(p, i, list.size(), list));
            }
        }
        return new RiskMatrix.Matrix(cells);
    }

    // ============================================================
    // 私有
    // ============================================================

    private void validateProject(Long projectId) {
        if (projectId == null) throw new BusinessException("projectId 不能为空");
        if (projectRepository.findByIdAndDeletedFalse(projectId).isEmpty()) {
            throw new BusinessException("Project not found: " + projectId);
        }
    }

    /** 批量 enrich: 1 次 IN 查询 owner, 1 次 IN 查询 wbsTask, 1 次 IN 查询 milestone */
    private List<RiskResponse> enrich(List<Risk> risks) {
        if (risks.isEmpty()) return List.of();
        Set<Long> ownerIds = new HashSet<>();
        Set<Long> wbsIds = new HashSet<>();
        Set<Long> msIds = new HashSet<>();
        for (Risk r : risks) {
            if (r.getOwnerUserId() != null) ownerIds.add(r.getOwnerUserId());
            if (r.getRelatedWbsTaskId() != null) wbsIds.add(r.getRelatedWbsTaskId());
            if (r.getRelatedMilestoneId() != null) msIds.add(r.getRelatedMilestoneId());
        }
        Map<Long, String> ownerById = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            for (AppUser u : userRepository.findAllById(ownerIds)) {
                ownerById.put(u.getId(), u.getFullName() != null ? u.getFullName() : u.getUsername());
            }
        }
        Map<Long, String> wbsById = new HashMap<>();
        if (!wbsIds.isEmpty()) {
            for (WbsTask t : wbsTaskRepository.findAllById(wbsIds)) {
                wbsById.put(t.getId(), t.getWbsCode() + " " + t.getName());
            }
        }
        Map<Long, String> msById = new HashMap<>();
        if (!msIds.isEmpty()) {
            for (Milestone m : milestoneRepository.findAllById(msIds)) {
                msById.put(m.getId(), m.getName());
            }
        }
        return risks.stream()
                .map(r -> RiskResponse.from(r,
                        r.getOwnerUserId() != null ? ownerById.get(r.getOwnerUserId()) : null,
                        r.getRelatedWbsTaskId() != null ? wbsById.get(r.getRelatedWbsTaskId()) : null,
                        r.getRelatedMilestoneId() != null ? msById.get(r.getRelatedMilestoneId()) : null))
                .toList();
    }

    private RiskResponse enrichOne(Risk r) {
        return enrich(List.of(r)).get(0);
    }

    private RiskResponseDto.Item toResponseItem(com.company.pmo.module.risk.RiskResponse resp) {
        String ownerName = null;
        if (resp.getOwnerUserId() != null) {
            ownerName = userRepository.findById(resp.getOwnerUserId())
                    .map(u -> u.getFullName() != null ? u.getFullName() : u.getUsername())
                    .orElse(null);
        }
        return new RiskResponseDto.Item(
                resp.getId(), resp.getRiskId(), resp.getAction(),
                resp.getOwnerUserId(), ownerName,
                resp.getDueDate(), resp.getCompletedAt(),
                resp.getStatus(), resp.getNote(), resp.getCreatedAt()
        );
        // 编译器 hint: 上面参数是按 RiskResponseDto.Item 构造签名顺序取的
    }

    private RiskHistoryItem toHistoryItem(RiskHistory h) {
        String opName = null;
        if (h.getOperatorId() != null) {
            opName = userRepository.findById(h.getOperatorId())
                    .map(u -> u.getFullName() != null ? u.getFullName() : u.getUsername())
                    .orElse(null);
        }
        return RiskHistoryItem.from(h, opName);
    }

    private void appendHistory(Long riskId, String action, String field, String oldV, String newV, String comment, Long operatorId) {
        RiskHistory h = new RiskHistory();
        h.setRiskId(riskId);
        h.setAction(action);
        h.setFieldName(field);
        h.setOldValue(oldV);
        h.setNewValue(newV);
        h.setComment(comment);
        h.setOperatorId(operatorId);
        historyRepository.save(h);
    }
}
