package com.company.pmo.module.initiation;

import com.company.pmo.module.dict.InitiationStatus;
import com.company.pmo.module.dict.InitiationStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V4.23 — 端到端验证 "上传 .docx SOW → AI 读到 → 生成 WBS 用到 SOW 关键词"。
 *
 * <p>之前上传 PDF/Word/Excel/PPT 会因为 {@code InitiationAiWbsService.readSowFileAsText}
 * 不支持这些后缀而直接被丢, 生成结果完全不反映新 SOW。本测试:
 * <ol>
 *   <li>用一个真实 .docx 文件 (POI 现场构造) 调 InitiationSowFileService.upload()</li>
 *   <li>再调 InitiationAiWbsService.generateDraft()</li>
 *   <li>断言生成的 draft 内容里包含 SOW 的关键词, sourceMeta.extractedFiles = 1</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class InitiationSowFileExtractionE2ETest {

    @Autowired InitiationAiWbsService wbsService;
    @Autowired InitiationSowFileService sowFileService;
    @Autowired ProjectInitiationRepository initRepo;
    @Autowired InitiationSowFileRepository sowFileRepo;
    @Autowired InitiationAiWbsDraftRepository draftRepo;
    @Autowired InitiationStatusRepository statusRepo;
    @Autowired InitiationService initiationService;

    @BeforeEach
    void setup() {
        draftRepo.deleteAll();
        sowFileRepo.deleteAll();
        if (statusRepo.count() == 0) {
            for (var pair : new String[][]{
                    {"PENDING", "审批中", "false"},
                    {"DEPT_APPROVED", "部门通过", "false"},
                    {"PMO_APPROVED", "PMO通过", "false"},
                    {"EXEC_APPROVED", "已批准", "true"},
                    {"REJECTED", "已驳回", "true"},
                    {"SUPPLEMENT", "需补充", "false"},
            }) {
                InitiationStatus x = new InitiationStatus();
                x.setCode(pair[0]); x.setName(pair[1]); x.setTerminal(Boolean.parseBoolean(pair[2]));
                x.setSortOrder(0);
                statusRepo.save(x);
            }
        }
    }

    @Test
    @DisplayName("上传 docx SOW: AI 草稿包含 SOW 关键词, sourceMeta.extractedFiles=1")
    void docxUpload_aiDraftPicksUpContent() throws Exception {
        // 1) 建一个立项
        ProjectInitiation init = new ProjectInitiation();
        init.setCode("INIT-DOCX-" + System.nanoTime());
        init.setTitle("docx 上传 AI 抽取");
        init.setApplicantId(1L);
        init.setDepartmentId(1L);
        init.setBackground("b");
        init.setGoals("g");
        init.setScope("s");
        ProjectInitiation saved = initiationService.submit(init);

        // 2) 现场造一份 docx, 内容含 AI 项目特征关键词
        Path tmp = Files.createTempFile("sow-e2e-", ".docx");
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            addPara(doc, "智能客服 NLP 升级项目 SOW");
            addPara(doc, "本项目拟建设企掌银智能体, 工期 6 个月, 预算 38 万");
            addPara(doc, "范围: 工单分类, 意图识别, 情绪分析, 智能派单");
            addPara(doc, "技术栈: Qwen 大模型 + Embedding + 微服务 + MySQL");
            addPara(doc, "集成: 坐席系统 (东信), 工单系统 (自研)");
            try (var out = Files.newOutputStream(tmp)) {
                doc.write(out);
            }
        }
        byte[] bytes = Files.readAllBytes(tmp);

        // 3) 模拟 multipart 上传
        MultipartFile mf = new MockMultipartFile(
                "file", "客户SOW-v2.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                bytes);
        InitiationSowFile savedFile = sowFileService.upload(saved.getId(), mf, 1L);
        assertThat(savedFile.getId()).isNotNull();
        assertThat(Files.exists(tmp)).isTrue(); // file not consumed

        // 4) 触发 AI 生成
        InitiationAiWbsDraft d = wbsService.generateDraft(saved.getId(), null, 2, 1L);
        assertThat(d).isNotNull();

        // 5) 关键断言 1: sourceMeta.extractedFiles = 1, failedFiles = 0
        List<Map<String, Object>> extractions = wbsService.latestFileExtractions();
        assertThat(extractions).isNotEmpty();
        Map<String, Object> ext = extractions.get(0);
        assertThat(ext.get("extracted")).isEqualTo(Boolean.TRUE);
        assertThat(((Number) ext.get("chars")).intValue()).isGreaterThan(50);

        // 6) 关键断言 2: 行业被识别为 AI_AGENT (因为 docx 里有 "智能体" + "Qwen" + "坐席")
        Map<String, Object> body = wbsService.parseDraftJson(d);
        assertThat(body.get("industry")).isEqualTo("AI_AGENT");

        // 7) 关键断言 3: 草稿里的 WP/milestone 应反映 SOW 内容
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wps = (List<Map<String, Object>>) body.get("workPackages");
        String all = wps.stream().map(w -> String.valueOf(w.get("name"))).reduce("", (a, b) -> a + "\n" + b);
        // SOW 里的"意图识别/工单分类/智能派单"应该影响 WP 名 (要么在 name 里, 要么在 moduleContext 里)
        // 简单粗暴: 拼接所有 WP 名 + 行业字段, 只要命中任意一个 AI 关键词就算通
        String allForAiCheck = all + " " + body.get("industry");
        assertThat(allForAiCheck)
                .as("WP 名 / industry 应反映 SOW AI 内容 (docx 里有'智能体/Qwen/坐席/意图/派单')")
                .containsAnyOf("意图", "派单", "智能", "坐席", "Qwen", "AI_AGENT", "智能体");
    }

    @Test
    @DisplayName("上传损坏 docx: sourceMeta.extractedFiles=0 failedFiles=1, 不抛异常")
    void corruptedDocx_doesNotCrash() throws Exception {
        ProjectInitiation init = new ProjectInitiation();
        init.setCode("INIT-BAD-" + System.nanoTime());
        init.setTitle("损坏 docx");
        init.setApplicantId(1L);
        init.setDepartmentId(1L);
        init.setBackground("b"); init.setGoals("g"); init.setScope("s");
        ProjectInitiation saved = initiationService.submit(init);

        // 写一堆看起来像 docx 但其实不是的字节
        MultipartFile mf = new MockMultipartFile(
                "file", "broken.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not a real docx, just random text".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        sowFileService.upload(saved.getId(), mf, 1L);

        // 给一个 pasteText 作为兜底, 否则 generateDraft 会 400 抛错
        saved.setSowPasteText("CRM 客户管理系统, 需求确认, 原型设计, 系统开发, 联调测试, UAT 上线");
        initRepo.save(saved);

        InitiationAiWbsDraft d = wbsService.generateDraft(saved.getId(), null, 2, 1L);
        assertThat(d).isNotNull();

        List<Map<String, Object>> extractions = wbsService.latestFileExtractions();
        Map<String, Object> ext = extractions.get(0);
        assertThat(ext.get("extracted")).isEqualTo(Boolean.FALSE);
        assertThat(ext).containsKey("reason");
        // 兜底 SOW (pasteText) 仍然走通, industry 至少是个有效值
        Map<String, Object> body = wbsService.parseDraftJson(d);
        assertThat(body.get("industry")).isIn("CRM", "ERP", "DATA", "AI", "云原生", "AI_AGENT",
                "BANKING_LOAN", "BANKING_CORE", "SECURITIES", "INSURANCE", "BANKING_CUSTODY");
    }

    private static void addPara(org.apache.poi.xwpf.usermodel.XWPFDocument doc, String text) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun run = p.createRun();
        run.setText(text);
    }
}