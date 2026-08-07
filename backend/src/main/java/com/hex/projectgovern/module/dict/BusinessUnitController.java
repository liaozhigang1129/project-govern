package com.hex.projectgovern.module.dict;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 业务单元 BU CRUD
 *  - 列表/详情任何登录用户可读
 *  - 增删改需 PMO_ADMIN
 */
@RestController
@RequestMapping("/dict/bus")
@RequiredArgsConstructor
@Tag(name = "Dictionaries - BU", description = "业务单元 (BU) 字典")
public class BusinessUnitController {

    private final BusinessUnitRepository repo;

    @GetMapping
    @RequireRoles.Dict
    @Operation(summary = "BU 列表")
    public ApiResponse<List<BusinessUnit>> list() {
        return ApiResponse.ok(repo.findAllByDeletedFalseOrderBySortOrderAscIdAsc());
    }

    @PostMapping
    @RequireRoles.Admin
    @AuditLog(module = "DICT", action = "BU_CREATE")
    public ApiResponse<BusinessUnit> create(@RequestBody BusinessUnit body) {
        if (body.getCode() == null || body.getCode().isBlank())
            throw new BusinessException("code required");
        if (repo.existsByCodeAndDeletedFalse(body.getCode()))
            throw new BusinessException("BU code exists: " + body.getCode());
        body.setId(null);
        body.setDeleted(false);
        return ApiResponse.ok(repo.save(body));
    }

    @PutMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "DICT", action = "BU_UPDATE", extractResourceId = false)
    public ApiResponse<BusinessUnit> update(@PathVariable Long id, @RequestBody BusinessUnit body) {
        BusinessUnit bu = repo.findById(id).orElseThrow(() -> new BusinessException(404, "BU not found"));
        if (body.getName() != null) bu.setName(body.getName());
        if (body.getDescription() != null) bu.setDescription(body.getDescription());
        if (body.getSortOrder() != 0) bu.setSortOrder(body.getSortOrder());
        bu.setEnabled(body.isEnabled());
        return ApiResponse.ok(repo.save(bu));
    }

    @DeleteMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "DICT", action = "BU_DELETE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        BusinessUnit bu = repo.findById(id).orElseThrow(() -> new BusinessException(404, "BU not found"));
        bu.setDeleted(true);
        repo.save(bu);
        return ApiResponse.ok(null);
    }
}
