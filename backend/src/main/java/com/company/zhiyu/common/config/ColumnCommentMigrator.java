package com.company.zhiyu.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列注释自动补齐 (MySQL 8.0+ 启动 Runner).
 *
 * <h3>背景</h3>
 * 早期 V2.2 误用 PG 的 COMMENT ON COLUMN, MySQL 跑不动. 本 Runner
 * 在 Flyway 之后启动, 动态从 INFORMATION_SCHEMA 取列类型, 拼 ALTER TABLE ... MODIFY ...
 * COMMENT 'xxx' 来补全. 幂等; 已存在 COMMENT 不会丢失 (脚本从原列定义读回).
 *
 * <h3>触发条件</h3>
 * <ul>
 *   <li>仅 MySQL dialect (PG 走 V2.2 原文件)</li>
 *   <li>通过 {@code pmo.column-comment.auto-fill=false} 可关闭</li>
 *   <li>{@code @Order(100)} 保证 Flyway 之后跑</li>
 * </ul>
 *
 * @author PMO
 * @since V2.2.1
 */
@Component
@Order(100)
@Slf4j
public class ColumnCommentMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final Environment env;

    /** (table, column) -> 中文注释 */
    private static final Map<String, String> COMMENTS = new LinkedHashMap<>();

    static {
        // ===== department =====
        COMMENTS.put("department|id",         "部门 ID(主键)");
        COMMENTS.put("department|name",       "部门名称");
        COMMENTS.put("department|code",       "部门编码(唯一,业务可见)");
        COMMENTS.put("department|parent_id",  "上级部门 ID,树形结构,顶层为 NULL");
        COMMENTS.put("department|sort_order", "同级排序号(asc)");
        COMMENTS.put("department|enabled",    "是否启用");
        COMMENTS.put("department|created_at", "创建时间");
        COMMENTS.put("department|updated_at", "更新时间(BEFORE UPDATE 触发器自动维护)");
        COMMENTS.put("department|deleted",    "软删标记");

        // ===== role =====
        COMMENTS.put("role|id",          "角色 ID");
        COMMENTS.put("role|code",        "角色编码: PM/DEPT_LEAD/PMO_ADMIN/EXEC/VIEWER");
        COMMENTS.put("role|name",        "角色名称(中文)");
        COMMENTS.put("role|description", "角色职责说明");
        COMMENTS.put("role|built_in",    "是否内置角色(内置不可删)");
        COMMENTS.put("role|created_at",  "创建时间");

        // ===== app_user =====
        COMMENTS.put("app_user|id",          "用户 ID(主键)");
        COMMENTS.put("app_user|username",    "登录名(唯一)");
        COMMENTS.put("app_user|email",       "邮箱(唯一)");
        COMMENTS.put("app_user|display_name","显示名");
        COMMENTS.put("app_user|phone",       "手机号");
        COMMENTS.put("app_user|enabled",     "是否启用");
        COMMENTS.put("app_user|dept_id",     "所属部门 ID");
        COMMENTS.put("app_user|created_at",  "创建时间");
        COMMENTS.put("app_user|updated_at",  "更新时间");
        COMMENTS.put("app_user|deleted",     "软删标记");

        // ===== business_unit =====
        COMMENTS.put("business_unit|id",          "BU ID");
        COMMENTS.put("business_unit|code",        "BU 编码(唯一,如 FIN/GOV)");
        COMMENTS.put("business_unit|name",        "BU 名称");
        COMMENTS.put("business_unit|description", "BU 说明");
        COMMENTS.put("business_unit|sort_order",  "排序号");
        COMMENTS.put("business_unit|enabled",     "是否启用");
        COMMENTS.put("business_unit|created_at",  "创建时间");
        COMMENTS.put("business_unit|updated_at",  "更新时间");
        COMMENTS.put("business_unit|deleted",     "软删标记");

        // ===== product_line =====
        COMMENTS.put("product_line|id",          "PL ID");
        COMMENTS.put("product_line|bu_id",       "所属 BU ID");
        COMMENTS.put("product_line|code",        "PL 编码(唯一,如 FIN-PAY)");
        COMMENTS.put("product_line|name",        "PL 名称");
        COMMENTS.put("product_line|description", "PL 说明");
        COMMENTS.put("product_line|sort_order",  "排序号");
        COMMENTS.put("product_line|enabled",     "是否启用");
        COMMENTS.put("product_line|created_at",  "创建时间");
        COMMENTS.put("product_line|updated_at",  "更新时间");
        COMMENTS.put("product_line|deleted",     "软删标记");

        // ===== related_product =====
        COMMENTS.put("related_product|id",          "关联产品 ID");
        COMMENTS.put("related_product|pl_id",       "所属 PL ID");
        COMMENTS.put("related_product|code",        "产品编码(唯一)");
        COMMENTS.put("related_product|name",        "产品名称");
        COMMENTS.put("related_product|description", "产品说明");
        COMMENTS.put("related_product|version",     "产品版本(可空)");
        COMMENTS.put("related_product|sort_order",  "排序号");
        COMMENTS.put("related_product|enabled",     "是否启用");
        COMMENTS.put("related_product|created_at",  "创建时间");
        COMMENTS.put("related_product|updated_at",  "更新时间");
        COMMENTS.put("related_product|deleted",     "软删标记");

        // ===== project =====
        COMMENTS.put("project|id",          "项目 ID");
        COMMENTS.put("project|code",        "项目编码(唯一)");
        COMMENTS.put("project|name",        "项目名称");
        COMMENTS.put("project|pl_id",       "所属产品线 ID");
        COMMENTS.put("project|bu_id",       "所属 BU ID");
        COMMENTS.put("project|pm_user_id",  "项目经理 user ID");
        COMMENTS.put("project|pl_user_id",  "PL owner user ID");
        COMMENTS.put("project|status",      "项目状态");
        COMMENTS.put("project|health",      "健康度");
        COMMENTS.put("project|start_date",  "计划开始日期");
        COMMENTS.put("project|end_date",    "计划结束日期");
        COMMENTS.put("project|budget",      "预算(元)");
        COMMENTS.put("project|created_at",  "创建时间");
        COMMENTS.put("project|updated_at",  "更新时间");
        COMMENTS.put("project|deleted",     "软删标记");

        // ===== project_type / project_status / health_level =====
        COMMENTS.put("project_type|id",   "项目类型 ID");
        COMMENTS.put("project_type|code", "类型编码");
        COMMENTS.put("project_type|name", "类型名称");
        COMMENTS.put("project_status|id",   "项目状态 ID");
        COMMENTS.put("project_status|code", "状态编码");
        COMMENTS.put("project_status|name", "状态名称");
        COMMENTS.put("health_level|id",   "健康度 ID");
        COMMENTS.put("health_level|code", "健康度编码");
        COMMENTS.put("health_level|name", "健康度名称");

        // ===== project_initiation / initiation_status =====
        COMMENTS.put("project_initiation|id",         "立项 ID");
        COMMENTS.put("project_initiation|project_id",  "项目 ID");
        COMMENTS.put("project_initiation|status",      "立项状态");
        COMMENTS.put("project_initiation|created_at", "创建时间");
        COMMENTS.put("initiation_status|id",   "立项状态 ID");
        COMMENTS.put("initiation_status|code", "状态编码");
        COMMENTS.put("initiation_status|name", "状态名称");

        // ===== milestone / milestone_status =====
        COMMENTS.put("milestone|id",            "里程碑 ID");
        COMMENTS.put("milestone|project_id",     "项目 ID");
        COMMENTS.put("milestone|phase_id",       "所属阶段 ID");
        COMMENTS.put("milestone|name",           "里程碑名称");
        COMMENTS.put("milestone|plan_date",      "计划日期");
        COMMENTS.put("milestone|actual_date",    "实际完成日期");
        COMMENTS.put("milestone|status",         "里程碑状态");
        COMMENTS.put("milestone|weight",         "权重(0-1)");
        COMMENTS.put("milestone|owner_user_id",  "责任人 user ID");
        COMMENTS.put("milestone|sequence",       "排序号");
        COMMENTS.put("milestone|deliverable",    "交付物");
        COMMENTS.put("milestone|remark",         "备注");
        COMMENTS.put("milestone|completed_at",   "实际完成时间");
        COMMENTS.put("milestone|created_at",     "创建时间");
        COMMENTS.put("milestone|updated_at",     "更新时间");
        COMMENTS.put("milestone|deleted",        "软删标记");
        COMMENTS.put("milestone_status|id",          "里程碑状态 ID");
        COMMENTS.put("milestone_status|code",        "状态编码");
        COMMENTS.put("milestone_status|name",        "状态名称");
        COMMENTS.put("milestone_status|is_terminal", "是否终态");

        // ===== approval =====
        COMMENTS.put("approval_record|id",          "审批记录 ID");
        COMMENTS.put("approval_record|biz_type",    "业务类型");
        COMMENTS.put("approval_record|biz_id",      "业务 ID");
        COMMENTS.put("approval_record|step_id",     "审批步骤 ID");
        COMMENTS.put("approval_record|approver_id", "审批人 user ID");
        COMMENTS.put("approval_record|decision",    "决策: APPROVE/REJECT");
        COMMENTS.put("approval_record|comment",     "审批意见");
        COMMENTS.put("approval_record|created_at",  "创建时间");
        COMMENTS.put("approval_step|id",          "审批步骤 ID");
        COMMENTS.put("approval_step|biz_type",    "业务类型");
        COMMENTS.put("approval_step|biz_id",      "业务 ID");
        COMMENTS.put("approval_step|step_order",  "步骤顺序");
        COMMENTS.put("approval_step|role_code",   "审批角色编码");
        COMMENTS.put("approval_step|approver_id", "审批人 user ID");
        COMMENTS.put("approval_step|status",      "状态");
        COMMENTS.put("approval_step|created_at",  "创建时间");

        // ===== notification =====
        COMMENTS.put("notification|id",        "通知 ID");
        COMMENTS.put("notification|user_id",   "接收人 user ID");
        COMMENTS.put("notification|type",      "通知类型");
        COMMENTS.put("notification|title",     "通知标题");
        COMMENTS.put("notification|content",   "通知内容");
        COMMENTS.put("notification|link",      "跳转链接");
        COMMENTS.put("notification|read_flag", "已读标记");
        COMMENTS.put("notification|created_at","创建时间");

        // ===== operation_log =====
        COMMENTS.put("operation_log|id",          "日志 ID");
        COMMENTS.put("operation_log|user_id",     "操作人 user ID");
        COMMENTS.put("operation_log|module",      "模块");
        COMMENTS.put("operation_log|action",      "操作");
        COMMENTS.put("operation_log|resource_id", "资源 ID");
        COMMENTS.put("operation_log|detail",      "详情(JSON)");
        COMMENTS.put("operation_log|created_at",  "创建时间");

        // ===== revoked_token =====
        COMMENTS.put("revoked_token|id",         "ID");
        COMMENTS.put("revoked_token|jti",        "JWT ID");
        COMMENTS.put("revoked_token|user_id",    "用户 ID");
        COMMENTS.put("revoked_token|revoked_at", "吊销时间");
        COMMENTS.put("revoked_token|expires_at", "过期时间");

        // ===== user_role =====
        COMMENTS.put("user_role|id",         "ID");
        COMMENTS.put("user_role|user_id",    "用户 ID");
        COMMENTS.put("user_role|role_id",    "角色 ID");
        COMMENTS.put("user_role|created_at", "创建时间");

        // ===== timesheet =====
        COMMENTS.put("timesheet_week|id",           "周报 ID");
        COMMENTS.put("timesheet_week|user_id",      "填报人 user ID");
        COMMENTS.put("timesheet_week|week_start",   "周开始日期(周一)");
        COMMENTS.put("timesheet_week|week_end",     "周结束日期(周日)");
        COMMENTS.put("timesheet_week|status",       "状态: DRAFT/SUBMITTED/APPROVED");
        COMMENTS.put("timesheet_week|total_hours",  "本周工时合计");
        COMMENTS.put("timesheet_week|remark",       "备注");
        COMMENTS.put("timesheet_week|created_at",   "创建时间");
        COMMENTS.put("timesheet_week|updated_at",   "更新时间");
        COMMENTS.put("timesheet_entry|id",            "工时明细 ID");
        COMMENTS.put("timesheet_entry|week_id",       "周报 ID");
        COMMENTS.put("timesheet_entry|project_id",    "项目 ID");
        COMMENTS.put("timesheet_entry|milestone_id",  "里程碑 ID(可空)");
        COMMENTS.put("timesheet_entry|work_date",     "工作日期");
        COMMENTS.put("timesheet_entry|hours",         "工时(h)");
        COMMENTS.put("timesheet_entry|description",   "工作内容");
        COMMENTS.put("timesheet_entry|created_at",    "创建时间");
        COMMENTS.put("timesheet_entry|updated_at",    "更新时间");

        // ===== user_im =====
        COMMENTS.put("user_im_binding|id",          "ID");
        COMMENTS.put("user_im_binding|user_id",     "用户 ID");
        COMMENTS.put("user_im_binding|channel",     "通道: wechat_work/dingtalk/feishu");
        COMMENTS.put("user_im_binding|external_id", "IM 平台用户 ID");
        COMMENTS.put("user_im_binding|enabled",     "是否启用");
        COMMENTS.put("user_im_binding|created_at",  "创建时间");
        COMMENTS.put("user_im_binding|updated_at",  "更新时间");
        COMMENTS.put("user_im_quiet_hours|id",         "ID");
        COMMENTS.put("user_im_quiet_hours|user_id",    "用户 ID");
        COMMENTS.put("user_im_quiet_hours|start_time", "起始时间(HH:mm)");
        COMMENTS.put("user_im_quiet_hours|end_time",   "结束时间(HH:mm)");
        COMMENTS.put("user_im_quiet_hours|timezone",   "时区(默认 Asia/Shanghai)");
        COMMENTS.put("user_im_quiet_hours|enabled",    "是否启用");
        COMMENTS.put("user_im_quiet_hours|created_at", "创建时间");
        COMMENTS.put("user_im_quiet_hours|updated_at", "更新时间");
    }

    public ColumnCommentMigrator(JdbcTemplate jdbc, Environment env) {
        this.jdbc = jdbc;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 1) 默认开; 可通过配置关闭
        if (!env.getProperty("pmo.column-comment.auto-fill", Boolean.class, true)) {
            return;
        }
        // 2) 仅 MySQL (PG 走 V2.2 原文件 COMMENT ON COLUMN)
        String url = env.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:mysql:")) {
            return;
        }

        long t0 = System.currentTimeMillis();
        int ok = 0, skip = 0, err = 0;
        for (Map.Entry<String, String> e : COMMENTS.entrySet()) {
            String[] tc = e.getKey().split("\\|", 2);
            String table = tc[0];
            String col   = tc[1];
            String cmt   = e.getValue().replace("'", "''");
            try {
                // 取原列定义 (类型, NOT NULL, DEFAULT, **EXTRA**: AUTO_INCREMENT 等)
                //
                // 关键: 必须保留 EXTRA (AUTO_INCREMENT / ON UPDATE CURRENT_TIMESTAMP 等),
                //       否则 MODIFY COLUMN 会把它们一起抹掉,导致后续 insert 失败 (例 operation_log.id 缺自增)。
                //       INFORMATION_SCHEMA.COLUMNS.COLUMN_TYPE 只含类型,不含 EXTRA,
                //       必须额外拼 EXTRA (例如 "auto_increment" / "DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3)")。
                List<String> rows = jdbc.queryForList(
                        "SELECT CONCAT(COLUMN_TYPE, IF(IS_NULLABLE='NO',' NOT NULL',''), " +
                        "IF(COLUMN_DEFAULT IS NULL,'',CONCAT(' DEFAULT ',QUOTE(COLUMN_DEFAULT))), " +
                        "EXTRA) " +
                        "FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?",
                        String.class, table, col);
                if (rows.isEmpty()) { skip++; continue; }   // 表/列不存在 (新模块未启用)
                String def = rows.get(0);
                String extra = rows.size() > 1 ? rows.get(1) : "";
                // V4.16.1 fix: def 末尾必须留空格, 否则与 extraClause 拼出 "NOT NULLauto_increment" 语法错
                if (!def.endsWith(" ")) def = def + " ";
                // extra 形如 "auto_increment" 或 "DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3)"
                // 大小写不敏感处理: 转小写 + 提取关键字,还原为 MySQL MODIFY COLUMN 期望的语法
                // 注意: javac 编译期 String + " " 会被优化成 makeConcatWithConstants 并丢掉尾随空格常量,
                //       必须用 StringBuilder.append 显式拼接, 不走 makeConcatWithConstants 优化
                StringBuilder extraClause = new StringBuilder();
                if (extra != null && !extra.isBlank()) {
                    String el = extra.toLowerCase();
                    if (el.contains("auto_increment")) {
                        extraClause.append("AUTO_INCREMENT").append(' ');
                    }
                    if (el.contains("on update current_timestamp")) {
                        extraClause.append("ON UPDATE CURRENT_TIMESTAMP(3)").append(' ');
                    }
                }
                String sql = "ALTER TABLE `" + table + "` MODIFY COLUMN `" + col + "` " +
                             def + extraClause + "COMMENT '" + cmt + "'";
                jdbc.execute(sql);
                ok++;
            } catch (Exception ex) {
                err++;
                log.warn("[ColumnComment] skip {}.{}: {}", table, col, ex.getMessage());
            }
        }
        log.info("[ColumnComment] done: ok={}, skip={}, err={}, cost={}ms",
                 ok, skip, err, System.currentTimeMillis() - t0);
    }
}
