package com.hex.projectgovern.module.initiation;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.security.RequireRoles;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@Tag(name = "Initiations / SOW 文件", description = "立项 SOW 上传 / 下载 / 元数据")
@RestController
@RequestMapping("/initiations/{id}/sow")
@RequiredArgsConstructor
public class InitiationSowFileController {

    private final InitiationSowFileService service;
    private final UserRepository userRepository;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "UPLOAD_SOW", extractResourceId = false)
    @Operation(summary = "上传 SOW 文件", description = "支持 .pdf/.doc/.docx/.md/.xlsx/.pptx,最大 50MB")
    public ApiResponse<InitiationSowFile> upload(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails ud) {
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();
        return ApiResponse.ok(service.upload(id, file, user.getId()));
    }

    @GetMapping
    @RequireRoles.Read
    @Operation(summary = "某立项下的 SOW 文件列表(按上传时间倒序)")
    public ApiResponse<List<InitiationSowFile>> list(@PathVariable Long id) {
        return ApiResponse.ok(service.list(id));
    }

    @GetMapping("/{fileId}/download")
    @RequireRoles.Read
    @Operation(summary = "下载 SOW 文件")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId) {
        InitiationSowFile f = service.list(id).stream()
                .filter(x -> x.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new com.hex.projectgovern.common.exception.BusinessException(404, "SOW file not found: " + fileId));
        Path path = service.resolveFile(f);
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new com.hex.projectgovern.common.exception.BusinessException(404, "File not on disk: " + path);
        }
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        if (f.getContentType() != null) {
            try { mt = MediaType.parseMediaType(f.getContentType()); } catch (Exception ignore) {}
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.getFileName() + "\"")
                .contentType(mt)
                .body(resource);
    }

    @DeleteMapping("/{fileId}")
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "DELETE_SOW", extractResourceId = false)
    @Operation(summary = "软删除 SOW 文件(元数据 deleted=true,实体文件保留)")
    public ApiResponse<Void> delete(@PathVariable Long id, @PathVariable Long fileId) {
        service.softDelete(fileId);
        return ApiResponse.ok(null);
    }
}
