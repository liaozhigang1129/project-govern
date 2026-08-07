package com.company.pmo.module.workload;

import com.company.pmo.common.api.ApiResponse;
import com.company.pmo.module.timesheet.TimesheetService;
import com.company.pmo.module.workload.dto.WorkloadDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * P2.B 人员负载查询 API。
 * <ul>
 *   <li>GET /api/workload/users — 人员 × 周 矩阵(PMO 看板)</li>
 *   <li>GET /api/workload/projects/{id} — 单项目汇总(给甘特/项目详情)</li>
 * </ul>
 */
@RestController
@RequestMapping("/workload")
@RequiredArgsConstructor
public class WorkloadController {

    private final WorkloadService service;

    /**
     * 人员 × 周 矩阵。
     *
     * @param departmentId 部门过滤(可选,PMO 看全员可省)
     * @param userId       仅看某人(可选,PM 看自己时可省 userId=自己)
     * @param from         起始周(默认:本周一)
     * @param to           截止周(默认:4 周后)
     */
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('PMO_ADMIN', 'EXEC', 'DEPT_LEAD', 'PM', 'VIEWER')")
    public ApiResponse<WorkloadDtos.UserLoadMatrix> userMatrix(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate today = LocalDate.now();
        LocalDate defFrom = WorkloadService.mondayOf(today);
        LocalDate defTo   = defFrom.plusWeeks(3);
        LocalDate f = from != null ? LocalDate.parse(from) : defFrom;
        LocalDate t = to   != null ? LocalDate.parse(to)   : defTo;
        return ApiResponse.ok(service.userLoadMatrix(departmentId, userId, f, t));
    }

    /** P2.5: 单人某周里程碑列表(下钻用) */
    @GetMapping("/users/{userId}/milestones")
    @PreAuthorize("hasAnyRole('PMO_ADMIN', 'EXEC', 'DEPT_LEAD', 'PM', 'VIEWER')")
    public ApiResponse<WorkloadDtos.UserMilestoneList> userMilestones(
            @PathVariable Long userId,
            @RequestParam String weekStart,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate ws = LocalDate.parse(weekStart);
        LocalDate today = LocalDate.now();
        LocalDate f = from != null ? LocalDate.parse(from) : today.minusDays(30);
        LocalDate t = to   != null ? LocalDate.parse(to)   : today.plusDays(60);
        return ApiResponse.ok(service.userMilestones(userId, ws, f, t));
    }

    /** 单项目工时汇总 */
    @GetMapping("/projects/{id}")
    @PreAuthorize("hasAnyRole('PMO_ADMIN', 'EXEC', 'DEPT_LEAD', 'PM', 'VIEWER')")
    public ApiResponse<WorkloadDtos.ProjectLoad> projectLoad(
            @PathVariable Long id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate today = LocalDate.now();
        LocalDate defFrom = today.minusDays(30);
        LocalDate defTo   = today.plusDays(7);
        LocalDate f = from != null ? LocalDate.parse(from) : defFrom;
        LocalDate t = to   != null ? LocalDate.parse(to)   : defTo;
        return ApiResponse.ok(service.projectLoad(id, f, t));
    }
}
