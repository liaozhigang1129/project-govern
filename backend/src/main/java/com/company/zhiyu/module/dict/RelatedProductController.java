package com.company.zhiyu.module.dict;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.audit.AuditLog;
import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 关联产品 CRUD
 *  - 列表/详情任何登录用户可读
 *  - 增删改需 PMO_ADMIN
 *  - 列表支持 ?plId=xx 过滤
 */
@RestController
@RequestMapping("/dict/related-products")
@RequiredArgsConstructor
@Tag(name = "Dictionaries - RelatedProduct", description = "关联产品 字典")
public class RelatedProductController {

    private final RelatedProductRepository rpRepo;
    private final ProductLineRepository plRepo;

    @GetMapping
    @RequireRoles.Dict
    @Operation(summary = "关联产品列表(可选 ?plId=xx 过滤)")
    @Transactional(readOnly = true)
    public ApiResponse<List<RelatedProduct>> list(@RequestParam(required = false) Long plId) {
        List<RelatedProduct> list;
        if (plId != null) {
            list = rpRepo.findAllByPlIdAndDeletedFalseOrderBySortOrderAscIdAsc(plId);
        } else {
            list = rpRepo.findAllByDeletedFalseOrderBySortOrderAscIdAsc();
        }
        for (RelatedProduct rp : list) {
            if (rp.getPl() != null) {
                ProductLine pl = rp.getPl();
                ProductLine ref = new ProductLine();
                ref.setId(pl.getId()); ref.setCode(pl.getCode()); ref.setName(pl.getName());
                rp.setPl(ref);
            }
        }
        return ApiResponse.ok(list);
    }

    @PostMapping
    @RequireRoles.Admin
    @AuditLog(module = "DICT", action = "RP_CREATE")
    public ApiResponse<RelatedProduct> create(@RequestBody RelatedProduct body) {
        if (body.getCode() == null || body.getCode().isBlank())
            throw new BusinessException("code required");
        if (body.getPl() == null || body.getPl().getId() == null)
            throw new BusinessException("plId required");
        ProductLine pl = plRepo.findByIdAndDeletedFalse(body.getPl().getId())
                .orElseThrow(() -> new BusinessException("PL not found"));
        if (rpRepo.existsByCodeAndDeletedFalse(body.getCode()))
            throw new BusinessException("RelatedProduct code exists: " + body.getCode());
        body.setId(null);
        body.setPl(pl);
        body.setDeleted(false);
        RelatedProduct saved = rpRepo.save(body);
        // 避免返回 LAZY proxy,事务已结束会失败;清掉 pl 引用
        saved.setPl(new ProductLine());
        saved.getPl().setId(pl.getId());
        saved.getPl().setCode(pl.getCode());
        saved.getPl().setName(pl.getName());
        return ApiResponse.ok(saved);
    }

    @PutMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "DICT", action = "RP_UPDATE", extractResourceId = false)
    public ApiResponse<RelatedProduct> update(@PathVariable Long id, @RequestBody RelatedProduct body) {
        RelatedProduct rp = rpRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new BusinessException(404, "RP not found"));
        if (body.getName() != null) rp.setName(body.getName());
        if (body.getDescription() != null) rp.setDescription(body.getDescription());
        if (body.getVersion() != null) rp.setVersion(body.getVersion());
        if (body.getSortOrder() != 0) rp.setSortOrder(body.getSortOrder());
        rp.setEnabled(body.isEnabled());
        if (body.getPl() != null && body.getPl().getId() != null
                && !body.getPl().getId().equals(rp.getPl().getId())) {
            rp.setPl(plRepo.findByIdAndDeletedFalse(body.getPl().getId())
                    .orElseThrow(() -> new BusinessException("PL not found")));
        }
        RelatedProduct saved = rpRepo.save(rp);
        ProductLine pl = rp.getPl();
        saved.setPl(new ProductLine());
        saved.getPl().setId(pl.getId());
        saved.getPl().setCode(pl.getCode());
        saved.getPl().setName(pl.getName());
        return ApiResponse.ok(saved);
    }

    @DeleteMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "DICT", action = "RP_DELETE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        RelatedProduct rp = rpRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new BusinessException(404, "RP not found"));
        rp.setDeleted(true);
        rpRepo.save(rp);
        return ApiResponse.ok(null);
    }
}
