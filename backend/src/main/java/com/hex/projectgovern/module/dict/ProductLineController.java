package com.hex.projectgovern.module.dict;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品线 PL CRUD
 *  - 列表/详情任何登录用户可读
 *  - 增删改需 PMO_ADMIN
 *  - 列表支持 ?buId=xx 过滤(前端级联用)
 */
@RestController
@RequestMapping("/dict/pls")
@RequiredArgsConstructor
@Tag(name = "Dictionaries - PL", description = "产品线 (PL) 字典")
public class ProductLineController {

    private final ProductLineRepository plRepo;
    private final BusinessUnitRepository buRepo;

    @GetMapping
    @RequireRoles.Dict
    @Operation(summary = "PL 列表(可选 ?buId=xx 过滤)")
    @Transactional(readOnly = true)
    public ApiResponse<List<ProductLine>> list(@RequestParam(required = false) Long buId) {
        List<ProductLine> list;
        if (buId != null) {
            list = plRepo.findAllByBuIdAndDeletedFalseOrderBySortOrderAscIdAsc(buId);
        } else {
            list = plRepo.findAllByDeletedFalseOrderBySortOrderAscIdAsc();
        }
        // 把 LAZY 关联替换为游离对象(避免返回后序列化 no Session)
        for (ProductLine pl : list) {
            if (pl.getBu() != null) {
                BusinessUnit bu = pl.getBu();
                BusinessUnit ref = new BusinessUnit();
                ref.setId(bu.getId()); ref.setCode(bu.getCode()); ref.setName(bu.getName());
                pl.setBu(ref);
            }
        }
        return ApiResponse.ok(list);
    }

    @PostMapping
    @RequireRoles.Admin
    @AuditLog(module = "DICT", action = "PL_CREATE")
    public ApiResponse<ProductLine> create(@RequestBody ProductLine body) {
        if (body.getCode() == null || body.getCode().isBlank())
            throw new BusinessException("code required");
        if (body.getBu() == null || body.getBu().getId() == null)
            throw new BusinessException("buId required");
        BusinessUnit bu = buRepo.findById(body.getBu().getId())
                .filter(b -> !b.isDeleted()).orElseThrow(() -> new BusinessException("BU not found"));
        if (plRepo.existsByCodeAndDeletedFalse(body.getCode()))
            throw new BusinessException("PL code exists: " + body.getCode());
        body.setId(null);
        body.setBu(bu);
        body.setDeleted(false);
        ProductLine saved = plRepo.save(body);
        // 把 LAZY proxy 替换为含 id/code/name 的游离对象,避免后续序列化 no Session
        BusinessUnit buRef = new BusinessUnit();
        buRef.setId(bu.getId()); buRef.setCode(bu.getCode()); buRef.setName(bu.getName());
        saved.setBu(buRef);
        return ApiResponse.ok(saved);
    }

    @PutMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "DICT", action = "PL_UPDATE", extractResourceId = false)
    public ApiResponse<ProductLine> update(@PathVariable Long id, @RequestBody ProductLine body) {
        ProductLine pl = plRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new BusinessException(404, "PL not found"));
        if (body.getName() != null) pl.setName(body.getName());
        if (body.getDescription() != null) pl.setDescription(body.getDescription());
        if (body.getSortOrder() != 0) pl.setSortOrder(body.getSortOrder());
        pl.setEnabled(body.isEnabled());
        if (body.getBu() != null && body.getBu().getId() != null
                && !body.getBu().getId().equals(pl.getBu().getId())) {
            pl.setBu(buRepo.findById(body.getBu().getId()).orElseThrow(() -> new BusinessException("BU not found")));
        }
        ProductLine saved = plRepo.save(pl);
        BusinessUnit bu = saved.getBu();
        BusinessUnit buRef = new BusinessUnit();
        buRef.setId(bu.getId()); buRef.setCode(bu.getCode()); buRef.setName(bu.getName());
        saved.setBu(buRef);
        return ApiResponse.ok(saved);
    }

    @DeleteMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "DICT", action = "PL_DELETE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ProductLine pl = plRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new BusinessException(404, "PL not found"));
        pl.setDeleted(true);
        plRepo.save(pl);
        return ApiResponse.ok(null);
    }
}
