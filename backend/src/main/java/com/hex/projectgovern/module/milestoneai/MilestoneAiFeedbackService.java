package com.hex.projectgovern.module.milestoneai;

import com.hex.projectgovern.module.notification.MilestoneAdvisoryFeedbackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** P5-PM 反馈业务逻辑 (事务核心) */
@Service
@RequiredArgsConstructor
@Slf4j
public class MilestoneAiFeedbackService {

    private final MilestoneAiAdvisoryRepository advisoryRepo;
    private final MilestoneAiFeedbackRepository feedbackRepo;
    private final ApplicationEventPublisher eventPublisher;

    public static final String TYPE_ACCEPTED = "ACCEPTED";
    public static final String TYPE_REJECTED = "REJECTED";
    public static final String TYPE_MISLEAD  = "MISLEAD";
    public static final String TYPE_EXPIRED  = "EXPIRED";

    private static final List<String> VALID_TYPES =
            List.of(TYPE_ACCEPTED, TYPE_REJECTED, TYPE_MISLEAD, TYPE_EXPIRED);

    /**
     * 提交反馈 (3 步事务)
     *  1) 写 milestone_ai_feedback (审计)
     *  2) 写 milestone_ai_advisory.feedback_* (反范式)
     *  3) publishEvent → 异步: 增量学习 + KPI
     */
    @Transactional
    public MilestoneAiFeedback submit(
            Long advisoryId, String type, String reasonCode,
            String comment, Long feedbackBy, String ip,
            String modelVersion
    ) {
        if (type == null || !VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException("invalid feedback type: " + type);
        }
        MilestoneAiAdvisory a = advisoryRepo.findById(advisoryId)
                .orElseThrow(() -> new IllegalArgumentException("advisory not found: " + advisoryId));

        // 1) 写 feedback 表
        MilestoneAiFeedback fb = new MilestoneAiFeedback();
        fb.setAdvisoryId(advisoryId);
        fb.setFeedbackType(type);
        fb.setReasonCode(reasonCode);
        fb.setComment(comment);
        fb.setFeedbackBy(feedbackBy);
        fb.setFeedbackAt(Instant.now());
        fb.setModelVersion(modelVersion != null ? modelVersion : a.getModelVersion());
        fb.setIpAddress(ip);
        feedbackRepo.save(fb);

        // 2) 反范式写 advisory
        a.setFeedbackType(type);
        a.setFeedbackAt(fb.getFeedbackAt());
        a.setFeedbackNote(comment);
        if (TYPE_REJECTED.equals(type) || TYPE_MISLEAD.equals(type)) {
            a.setStatus("REJECTED");
            if (comment != null && !comment.isBlank()) a.setRejectReason(comment);
        } else if (TYPE_EXPIRED.equals(type)) {
            a.setStatus("EXPIRED");
        }
        advisoryRepo.save(a);

        // 3) 异步事件
        eventPublisher.publishEvent(new MilestoneAdvisoryFeedbackEvent(
                advisoryId, type, reasonCode, comment, feedbackBy,
                fb.getModelVersion(), fb.getFeedbackAt()
        ));

        log.info("[P5-Feedback] advisory={} type={} reason={} by={}",
                advisoryId, type, reasonCode, feedbackBy);
        return fb;
    }

    /** KPI 看板: 近 N 天反馈统计 */
    public Map<String, Object> kpi7d(int days) {
        Instant since = Instant.now().minusSeconds(days * 86400L);
        List<Object[]> rows = feedbackRepo.aggregateSince(since);
        Object[] row = rows.isEmpty() ? new Object[]{0, 0, 0, 0} : rows.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("since", since.toString());
        m.put("feedback_count", toInt(row[0]));
        m.put("accepted", toInt(row[1]));
        m.put("rejected", toInt(row[2]));
        m.put("misleads", toInt(row[3]));
        m.put("accepted_rate", toInt(row[0]) == 0 ? 0.0
                : Math.round(toInt(row[1]) * 10000.0 / toInt(row[0])) / 100.0);
        return m;
    }

    private static int toInt(Object o) { return o == null ? 0 : ((Number) o).intValue(); }
}
