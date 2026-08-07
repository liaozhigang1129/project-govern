package com.company.zhiyu.module.initiation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InitiationSowFileRepository extends JpaRepository<InitiationSowFile, Long> {

    /** 某立项下所有未删除的 SOW 文件,按时间倒序(最新在前) */
    List<InitiationSowFile> findByInitiationIdAndDeletedFalseOrderByUploadedAtDesc(Long initiationId);

    /** 取最新一个(用于 isSowReceived 校验) */
    Optional<InitiationSowFile> findFirstByInitiationIdAndDeletedFalseOrderByUploadedAtDesc(Long initiationId);
}
