package com.hex.projectgovern.module.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;

/**
 * 邮件配置。
 *
 * 设计:
 *  - JavaMailSender 完全由 Spring Boot MailSenderAutoConfiguration 自动装配
 *    (基于 application.yml 的 spring.mail.* 配置),我们不再写 @Primary 覆盖,
 *    避免误设了 auth=true 导致 SMTP handshake 失败
 *  - pmo.mail.enabled=false 时,MailService.send() 是 no-op,根本不发邮件
 *  - SimpleMailMessage 模板提供默认值,可被 MailService 覆盖
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    /** 一个普通 SimpleMailMessage 模板(可被多次 setFrom 覆盖) */
    @Bean
    public SimpleMailMessage defaultMailTemplate() {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom("project-govern <zg.liao@goupwith.com>");
        return m;
    }
}
