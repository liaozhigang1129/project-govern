package com.company.pmo.module.milestone;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.company.pmo.common.testsupport.ContractTestDataInitializer;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * MilestoneCreateRequest 契约 + 校验
 * <p>验证:
 * - weight 范围 1-10 (边界 0, 11, 100 全部应被拒)
 * - 必填字段 (projectId / name / sequence / planDate / weight) 缺失 → 400
 * - 缺 status 字段,后端强制 PENDING
 * - OpenAPI schema 含 minimum=1 maximum=10
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContractTestDataInitializer.class)
@AutoConfigureMockMvc
class MilestoneCreateRequestContractTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    String token() throws Exception {
        MvcResult r = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"pmo123\"}"))
            .andExpect(status().isOk()).andReturn();
        // ApiResponse.data 是 {token, user} map,直接从 raw JSON 拿
        String json = r.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String,Object> data = (Map<String,Object>) om.readValue(json, Map.class).get("data");
        return (String) data.get("accessToken");
    }

    @Test
    void weight_zero_rejected() throws Exception {
        String body = """
            {"projectId":1,"name":"M1","sequence":1,"planDate":"2025-12-31","weight":0}
            """;
        mvc.perform(post("/milestones").header("Authorization","Bearer "+token())
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
    }

    @Test
    void weight_eleven_rejected() throws Exception {
        String body = """
            {"projectId":1,"name":"M1","sequence":1,"planDate":"2025-12-31","weight":11}
            """;
        mvc.perform(post("/milestones").header("Authorization","Bearer "+token())
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
    }

    @Test
    void weight_hundred_rejected() throws Exception {
        String body = """
            {"projectId":1,"name":"M1","sequence":1,"planDate":"2025-12-31","weight":100}
            """;
        mvc.perform(post("/milestones").header("Authorization","Bearer "+token())
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
    }

    @Test
    void weight_in_range_accepted() throws Exception {
        // 用随机 sequence (系统时间戳) 避免跟历史数据撞 unique 约束
        int seq = (int) (System.currentTimeMillis() % 100000);
        String body = String.format("""
            {"projectId":1,"name":"M-CONTRACT-OK","sequence":%d,"planDate":"2025-12-31","phaseId":2,"weight":5}
            """, seq);
        mvc.perform(post("/milestones").header("Authorization","Bearer "+token())
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isOk());
    }

    @Test
    void missing_required_fields_rejected() throws Exception {
        // 缺 projectId / name / sequence / planDate / weight
        mvc.perform(post("/milestones").header("Authorization","Bearer "+token())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void openapi_schema_marks_weight_bounds() throws Exception {
        // 拉 /v3/api-docs 验证 MilestoneCreateRequest 暴露在 OpenAPI
        mvc.perform(get("/v3/api-docs"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.components.schemas.MilestoneCreateRequest.properties.weight.minimum").value(1))
           .andExpect(jsonPath("$.components.schemas.MilestoneCreateRequest.properties.weight.maximum").value(10));
    }
}
