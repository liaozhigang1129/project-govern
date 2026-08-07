package com.company.pmo.module.initiation;

import com.company.pmo.common.api.ApiResponse;
import com.company.pmo.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 立项 SOW 文件上传 / 下载 / 元数据服务。
 * <p>实体存到 {@code ${app.upload.sow-dir:uploads/sow}/{initiationId}/{uuid}-{filename}},
 * 数据库只存相对路径(filePath)+ 元数据,便于日后切到 MinIO/S3(只改 storageRoot)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitiationSowFileService {

    private final InitiationSowFileRepository repo;
    private final ProjectInitiationRepository initiationRepo;

    @Value("${app.upload.sow-dir:uploads/sow}")
    private String storageRoot;

    /** 允许的扩展名白名单 */
    private static final List<String> ALLOWED_EXTS = List.of(".pdf", ".doc", ".docx", ".md", ".txt", ".xlsx", ".xls", ".pptx");

    /** 单文件最大 50MB */
    private static final long MAX_SIZE = 50L * 1024 * 1024;

    /**
     * 上传 SOW 文件:
     * <ol>
     *   <li>校验 initiation 存在</li>
     *   <li>校验扩展名 + 大小</li>
     *   <li>写文件到 ${storageRoot}/{initiationId}/{uuid}-{safeFilename}</li>
     *   <li>写 initiation_sow_file 元数据</li>
     *   <li>更新 project_initiation.sowReceived = true</li>
     * </ol>
     */
    @Transactional
    public InitiationSowFile upload(Long initiationId, MultipartFile file, Long uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "File is empty");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(400, "File too large (max 50MB): " + file.getSize() + " bytes");
        }
        String origName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = origName.lastIndexOf('.');
        if (dot >= 0) {
            ext = origName.substring(dot).toLowerCase();
        }
        if (ext.isBlank() || !ALLOWED_EXTS.contains(ext)) {
            throw new BusinessException(400, "Unsupported file extension: " + ext + " (allowed: " + ALLOWED_EXTS + ")");
        }
        // 校验立项存在
        ProjectInitiation init = initiationRepo.findById(initiationId)
                .filter(i -> !i.isDeleted())
                .orElseThrow(() -> new BusinessException(404, "Initiation not found: " + initiationId));

        // 写盘
        String safeBase = origName.replaceAll("[^\\w\\u4e00-\\u9fa5.\\-]", "_");
        String storageFileName = UUID.randomUUID().toString().substring(0, 8) + "-" + safeBase;
        Path dir = Paths.get(storageRoot, String.valueOf(initiationId));
        Path target = dir.resolve(storageFileName);
        try {
            Files.createDirectories(dir);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to write SOW file: {}", e.getMessage(), e);
            throw new BusinessException(500, "Failed to write SOW file: " + e.getMessage());
        }

        // 元数据
        InitiationSowFile entity = new InitiationSowFile();
        entity.setInitiationId(initiationId);
        entity.setFileName(origName);
        entity.setFilePath(initiationId + "/" + storageFileName); // 存相对 storageRoot 路径(老数据兼容:resolveFile 同时兼容两种)
        entity.setFileSize(file.getSize());
        // V4.22 修复: 浏览器 multipart/form-data 的 Content-Type 形如
        //   "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW"
        // 整体长度 70~200 字节, 直接塞进 VARCHAR(64) 会触发 MySQL "Data too long"。
        // 这里只保留 type/subtype 部分 (e.g. "application/pdf"), 同时二次截断防越界。
        String rawContentType = file.getContentType();
        String safeContentType = sanitizeContentType(rawContentType);
        entity.setContentType(safeContentType);
        entity.setUploadedBy(uploadedBy);
        entity.setUploadedAt(Instant.now());
        InitiationSowFile saved = repo.save(entity);

        // 更新立项 sowReceived 标志
        init.setSowReceived(true);
        initiationRepo.save(init);

        log.info("[SowUpload] initiation={} file={} size={}B by user={}", initiationId, origName, file.getSize(), uploadedBy);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InitiationSowFile> list(Long initiationId) {
        return repo.findByInitiationIdAndDeletedFalseOrderByUploadedAtDesc(initiationId);
    }

    /** 加载文件绝对路径(供下载端点用) */
    public Path resolveFile(InitiationSowFile f) {
        Path p = Paths.get(f.getFilePath());
        if (!p.isAbsolute()) {
            // 兼容历史数据:filePath 可能已含 storageRoot 前缀 (老 bug)
            String sp = p.toString().replace('\\', '/');
            String root = storageRoot.replace('\\', '/');
            if (sp.startsWith(root + "/")) {
                // filePath 已含 storageRoot,不要再 prepend
                p = p.toAbsolutePath();
            } else {
                p = Paths.get(storageRoot).toAbsolutePath().resolve(p);
            }
        }
        return p;
    }

    /** 软删除文件(元数据 deleted=true,实体文件保留供审计) */
    @Transactional
    public void softDelete(Long fileId) {
        repo.findById(fileId).ifPresent(f -> {
            f.setDeleted(true);
            repo.save(f);
            // 重新评估 sowReceived
            List<InitiationSowFile> remaining = repo.findByInitiationIdAndDeletedFalseOrderByUploadedAtDesc(f.getInitiationId());
            initiationRepo.findById(f.getInitiationId()).ifPresent(init -> {
                init.setSowReceived(!remaining.isEmpty());
                initiationRepo.save(init);
            });
        });
    }

    public ApiResponse<InitiationSowFile> wrap(InitiationSowFile f) {
        return ApiResponse.ok(f);
    }

    /**
     * 规范化 Content-Type: 截掉 multipart 边界参数、去掉空白, 限制在列宽以内。
     * - 入参 "multipart/form-data; boundary=----WebKit..." → "multipart/form-data"
     * - 入参 "  APPLICATION/PDF " → "application/pdf"
     * - 入参 null 或超长 → 截断到 60 字符 (留 4 字节余量防 charset 后缀)
     */
    static String sanitizeContentType(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) return null;
        int semi = s.indexOf(';');
        if (semi >= 0) s = s.substring(0, semi).trim();
        if (s.isEmpty()) return null;
        // 安全网: 表列宽 VARCHAR(64), 留 4 字节给将来可能追加的 "; charset=utf-8"
        if (s.length() > 60) s = s.substring(0, 60);
        return s;
    }
}
