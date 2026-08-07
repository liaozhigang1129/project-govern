package com.company.zhiyu.module.notification;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.notification.dto.UserImQuietHoursDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DND 勿扰时段业务服务(P2 #2)。
 *
 * 规则:
 *  - 时间格式 HH:mm 24h(由 DTO @Pattern 校验)
 *  - 允许 start == end 的空配置(保存,运行时匹配返回 false)
 *  - 删除是硬删(数据量小,无审计需求)
 *  - 单用户窗口数:不限(实际中 2-3 个,午餐/深夜)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserImQuietHoursService {

    private final UserImQuietHoursRepository repo;

    public List<UserImQuietHoursDtos.View> list(Long userId) {
        if (userId == null) {
            throw new BusinessException("userId is required for list DND windows");
        }
        return repo.findByUserId(userId).stream().map(this::toView).collect(Collectors.toList());
    }

    @Transactional
    public UserImQuietHoursDtos.View create(UserImQuietHoursDtos.CreateReq req) {
        UserImQuietHours w = UserImQuietHours.builder()
                .userId(req.userId())
                .startTime(req.startTime())
                .endTime(req.endTime())
                .timezone(req.timezone() == null || req.timezone().isBlank()
                        ? "Asia/Shanghai" : req.timezone())
                .enabled(true)
                .build();
        UserImQuietHours saved = repo.save(w);
        log.info("[DND] created id={} userId={} window={}-{} tz={}",
                saved.getId(), saved.getUserId(),
                saved.getStartTime(), saved.getEndTime(), saved.getTimezone());
        return toView(saved);
    }

    @Transactional
    public UserImQuietHoursDtos.View update(Long id, UserImQuietHoursDtos.UpdateReq req) {
        UserImQuietHours w = repo.findById(id)
                .orElseThrow(() -> new BusinessException("DND window not found: " + id));
        if (req.startTime() != null && !req.startTime().isBlank()) w.setStartTime(req.startTime());
        if (req.endTime() != null && !req.endTime().isBlank())     w.setEndTime(req.endTime());
        if (req.timezone() != null && !req.timezone().isBlank())   w.setTimezone(req.timezone());
        if (req.enabled() != null)                                  w.setEnabled(req.enabled());
        log.info("[DND] updated id={} window={}-{} tz={} enabled={}",
                w.getId(), w.getStartTime(), w.getEndTime(), w.getTimezone(), w.isEnabled());
        return toView(w);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new BusinessException("DND window not found: " + id);
        }
        repo.deleteById(id);
        log.info("[DND] deleted id={}", id);
    }

    private UserImQuietHoursDtos.View toView(UserImQuietHours w) {
        return new UserImQuietHoursDtos.View(
                w.getId(), w.getUserId(), w.getStartTime(), w.getEndTime(),
                w.getTimezone(), w.isEnabled(), w.getCreatedAt(), w.getUpdatedAt());
    }
}
