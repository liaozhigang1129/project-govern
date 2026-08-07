package com.company.pmo.module.notification;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.notification.dto.UserImBindingDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UserIMBinding 业务服务(P2-A)。
 *
 * 规则:
 *  - 唯一约束 (user_id, channel): 同一用户同一 IM 平台只允许一条
 *  - channel 取值: NotificationChannel.Type.code() 一致(wechat_work/dingtalk/feishu)
 *  - 删除是硬删(数据量小,且 binding 没有审计需求)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserImBindingService {

    private final UserImBindingRepository repo;

    public List<UserImBindingDtos.View> list(Long userId) {
        List<UserImBinding> rows = (userId == null)
                ? repo.findAll()
                : repo.findByUserIdAndEnabledTrue(userId);
        return rows.stream().map(this::toView).collect(Collectors.toList());
    }

    public UserImBindingDtos.View get(Long id) {
        return toView(repo.findById(id)
                .orElseThrow(() -> new BusinessException("UserImBinding not found: " + id)));
    }

    @Transactional
    public UserImBindingDtos.View create(UserImBindingDtos.CreateReq req) {
        if (NotificationChannel.Type.fromCode(req.channel()) == null) {
            throw new BusinessException("invalid channel: " + req.channel()
                    + " (allowed: wechat_work/dingtalk/feishu)");
        }
        if (repo.findByUserIdAndChannel(req.userId(), req.channel()).isPresent()) {
            throw new BusinessException("binding already exists: userId=" + req.userId()
                    + " channel=" + req.channel());
        }
        UserImBinding b = UserImBinding.builder()
                .userId(req.userId())
                .channel(req.channel())
                .externalUserId(req.externalUserId())
                .enabled(true)
                .build();
        UserImBinding saved = repo.save(b);
        log.info("[UserImBinding] created id={} userId={} channel={}", saved.getId(),
                saved.getUserId(), saved.getChannel());
        return toView(saved);
    }

    @Transactional
    public UserImBindingDtos.View update(Long id, UserImBindingDtos.UpdateReq req) {
        UserImBinding b = repo.findById(id)
                .orElseThrow(() -> new BusinessException("UserImBinding not found: " + id));
        if (req.externalUserId() != null && !req.externalUserId().isBlank()) {
            b.setExternalUserId(req.externalUserId());
        }
        if (req.enabled() != null) {
            b.setEnabled(req.enabled());
        }
        log.info("[UserImBinding] updated id={} externalUserId={} enabled={}",
                b.getId(), b.getExternalUserId(), b.isEnabled());
        return toView(b);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new BusinessException("UserImBinding not found: " + id);
        }
        repo.deleteById(id);
        log.info("[UserImBinding] deleted id={}", id);
    }

    /** 取某用户已启用的所有 binding(按 channel 分组) */
    public Map<String, List<UserImBinding>> findByUserGrouped(Long userId) {
        return repo.findByUserIdAndEnabledTrue(userId).stream()
                .collect(Collectors.groupingBy(UserImBinding::getChannel));
    }

    private UserImBindingDtos.View toView(UserImBinding b) {
        return new UserImBindingDtos.View(
                b.getId(), b.getUserId(), b.getChannel(), b.getExternalUserId(),
                b.isEnabled(), b.getCreatedAt(), b.getUpdatedAt());
    }
}
