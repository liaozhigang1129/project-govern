package com.hex.projectgovern.module.initiation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InitiationAiWbsDraftRepository extends JpaRepository<InitiationAiWbsDraft, Long> {

    /** 某立项的所有草稿(含已应用),按时间倒序 */
    List<InitiationAiWbsDraft> findByInitiationIdOrderByCreatedAtDesc(Long initiationId);

    /** 最近一个未应用的草稿(Step 3 入口取这个) */
    Optional<InitiationAiWbsDraft> findFirstByInitiationIdAndAppliedAtIsNullOrderByCreatedAtDesc(Long initiationId);
}
