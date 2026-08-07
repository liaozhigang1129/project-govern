package com.company.pmo.module.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;

/**
 * P2 #7 — Flowable 流程引擎配置 + 自动部署。
 *
 * 设计要点:
 *  - flowable-spring-boot-starter 自动注入 ProcessEngine / RepositoryService / TaskService 等
 *  - 表前缀 act_ 由 starter 自动建表(act_re_procdef / act_ru_task / act_hi_taskinst ...)
 *  - application.yml 设 flowable.* 关掉自动部署,改用本类显式部署,便于版本管理
 *  - 仅当 DB 尚无该 key 的 deployment 时部署(可重复启动,幂等)
 */
@Slf4j
@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class FlowableConfig {

    private final RepositoryService repositoryService;

    /**
     * 应用启动完毕后,部署立项审批 bpmn。
     * 用 ApplicationReadyEvent 避免与 Flyway 建表竞态。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void deployInitiationProcess() {
        String deploymentName = "pmo-initiation-v1";
        String resource = "bpmn/initiation-approval.bpmn20.xml";

        long existing = repositoryService.createDeploymentQuery()
                .deploymentName(deploymentName)
                .count();
        if (existing > 0) {
            log.info("[Flowable] 流程 {} 已存在,跳过部署 (count={})", deploymentName, existing);
            return;
        }

        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            Deployment d = repositoryService.createDeployment()
                    .name(deploymentName)
                    .addInputStream(resource, in)
                    .deploy();
            log.info("[Flowable] 部署成功: deploymentId={} name={} key=initiation-approval",
                    d.getId(), d.getName());
        } catch (IOException e) {
            log.error("[Flowable] 读取 bpmn {} 失败: {}", resource, e.getMessage(), e);
            throw new RuntimeException("Flowable BPMN deploy failed", e);
        } catch (Exception e) {
            log.error("[Flowable] 部署失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}
