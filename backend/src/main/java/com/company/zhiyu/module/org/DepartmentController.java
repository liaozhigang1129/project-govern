package com.company.zhiyu.module.org;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@RequireRoles.Read
@Tag(name = "Departments", description = "部门查询")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    @GetMapping
    @Operation(summary = "部门列表 (按 sortOrder 升序)")
    public ApiResponse<List<Department>> list() {
        return ApiResponse.ok(departmentRepository.findAllByDeletedFalseOrderBySortOrderAscIdAsc());
    }
}
