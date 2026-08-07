package com.hex.projectgovern.module.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.hex.projectgovern.common.testsupport.ContractTestDataInitializer;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * GET /api/projects/{id}/overview 契约测试
 * <p>验证:
 * - 一次请求拿全 详情 + 里程碑列表 + 加权进度
 * - 字典嵌套(type/status/health/status 嵌套)
 * - 不存在 id → 404
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContractTestDataInitializer.class)
@AutoConfigureMockMvc
class ProjectOverviewContractTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    String token() throws Exception {
        MvcResult r = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"pmo123\"}"))
            .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String,Object> data = (Map<String,Object>) om.readValue(
                r.getResponse().getContentAsString(), Map.class).get("data");
        return (String) data.get("accessToken");
    }

    @Test
    void overview_returns_aggregated_payload() throws Exception {
        mvc.perform(get("/projects/1/overview").header("Authorization","Bearer "+token()))
           .andExpect(status().isOk())
           // project 字段
           .andExpect(jsonPath("$.data.project.id").value(1))
           .andExpect(jsonPath("$.data.project.name").exists())
           .andExpect(jsonPath("$.data.project.type.code").value("DELIVERY"))
           .andExpect(jsonPath("$.data.project.type.name").value("客户交付"))
           .andExpect(jsonPath("$.data.project.status.code").exists())
           .andExpect(jsonPath("$.data.project.health.code").exists())
           // milestones 是数组
           .andExpect(jsonPath("$.data.milestones").isArray())
           // progressPct 是 0-100 整数
           .andExpect(jsonPath("$.data.progressPct").isNumber());
    }

    @Test
    void milestone_nested_status_dict() throws Exception {
        mvc.perform(get("/projects/1/overview").header("Authorization","Bearer "+token()))
           .andExpect(status().isOk())
           // milestone 0 至少有 status.id code name + weight
           .andExpect(jsonPath("$.data.milestones[0].status.code").exists())
           .andExpect(jsonPath("$.data.milestones[0].status.name").exists())
           .andExpect(jsonPath("$.data.milestones[0].weight").isNumber());
    }

    @Test
    void overview_404_for_missing_project() throws Exception {
        mvc.perform(get("/projects/999999/overview").header("Authorization","Bearer "+token()))
           .andExpect(status().is4xxClientError());
    }
}
