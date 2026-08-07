package com.company.pmo.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI pmoOpenAPI() {
        final String jwtScheme = "bearer-jwt";
        return new OpenAPI()
                .info(new Info()
                        .title("PMO Project Management System API")
                        .version("0.1.0")
                        .description("""
                            公司级项目治理系统 MVP API。

                            ## 模块
                            - **Auth**: JWT 登录
                            - **Dashboard**: 4 项 KPI + 状态/健康度分布
                            - **Projects**: 项目主数据 CRUD
                            - **Initiations**: 立项 + 3 级审批流转 (DEPT_LEAD → PMO_ADMIN → EXEC)
                            - **Milestones**: 里程碑 + 加权进度 (JPQL 聚合)
                            - **Dictionaries**: 字典查询
                            - **Users / Departments**: 组织管理

                            ## 通用约定
                            - 所有响应: `ApiResponse<T>` {code, message, data, timestamp}
                            - code=0 表示成功,其他为业务异常
                            - 所有写接口需 `Authorization: Bearer <token>` (除 /auth/login)
                            """)
                        .license(new License().name("Internal Use Only")))
                .addSecurityItem(new SecurityRequirement().addList(jwtScheme))
                .components(new Components().addSecuritySchemes(jwtScheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
