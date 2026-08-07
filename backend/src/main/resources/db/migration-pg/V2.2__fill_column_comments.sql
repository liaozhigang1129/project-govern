-- ============================================================
-- V2.2 补全所有业务表字段描述
-- ============================================================
-- 目的: 为已有表的字段补 COMMENT ON COLUMN,便于 DBA / 文档生成
--       已有的 COMMENT 保持不动(此脚本用 IF NOT EXISTS-style: 不重写历史)
--       PG 支持 COMMENT ON COLUMN,MySQL 8.0+ 也支持 ALTER TABLE ... MODIFY
-- 注: 此脚本仅在 PG 跑(separate file for mysql exists)
-- ============================================================

-- ---------- V1.0 函数 ----------
COMMENT ON FUNCTION pmo.fn_set_updated_at() IS '通用 updated_at 自动维护(BEFORE UPDATE 触发器)';

-- ---------- department ----------
COMMENT ON COLUMN department.id          IS '部门 ID(主键)';
COMMENT ON COLUMN department.name        IS '部门名称';
COMMENT ON COLUMN department.code        IS '部门编码(唯一,业务可见)';
COMMENT ON COLUMN department.parent_id   IS '上级部门 ID,树形结构,顶层为 NULL';
COMMENT ON COLUMN department.sort_order  IS '同级排序号(asc)';
COMMENT ON COLUMN department.enabled     IS '是否启用';
COMMENT ON COLUMN department.created_at  IS '创建时间';
COMMENT ON COLUMN department.updated_at  IS '更新时间(BEFORE UPDATE 触发器自动维护)';
COMMENT ON COLUMN department.deleted     IS '软删标记';

-- ---------- role ----------
COMMENT ON COLUMN role.id              IS '角色 ID';
COMMENT ON COLUMN role.code            IS '角色编码: PM/DEPT_LEAD/PMO_ADMIN/EXEC/VIEWER';
COMMENT ON COLUMN role.name            IS '角色名称(中文)';
COMMENT ON COLUMN role.description     IS '角色职责说明';
COMMENT ON COLUMN role.built_in        IS '是否内置角色(内置不可删)';
COMMENT ON COLUMN role.created_at      IS '创建时间';

-- ---------- app_user ----------
COMMENT ON COLUMN app_user.id              IS '用户 ID';
COMMENT ON COLUMN app_user.username        IS '登录用户名(唯一)';
COMMENT ON COLUMN app_user.password_hash   IS 'BCrypt 密码哈希';
COMMENT ON COLUMN app_user.full_name       IS '真实姓名';
COMMENT ON COLUMN app_user.email           IS '邮箱(唯一)';
COMMENT ON COLUMN app_user.phone           IS '手机号';
COMMENT ON COLUMN app_user.department_id   IS '所属部门 ID';
COMMENT ON COLUMN app_user.primary_role_id IS '主角色 ID(role.id)';
COMMENT ON COLUMN app_user.job_title       IS '岗位(如:项目经理/开发/测试)';
COMMENT ON COLUMN app_user.enabled         IS '是否启用';
COMMENT ON COLUMN app_user.last_login_at   IS '最近登录时间';
COMMENT ON COLUMN app_user.created_at      IS '创建时间';
COMMENT ON COLUMN app_user.updated_at      IS '更新时间';
COMMENT ON COLUMN app_user.deleted         IS '软删标记';
COMMENT ON COLUMN app_user.backup_user_id  IS '备份审批人(主审缺席时代审,V1.8 P1.5)';

-- ---------- user_role ----------
COMMENT ON COLUMN user_role.id         IS '关联 ID';
COMMENT ON COLUMN user_role.user_id    IS '用户 ID';
COMMENT ON COLUMN user_role.role_id    IS '角色 ID';
COMMENT ON COLUMN user_role.created_at IS '授权时间';

-- ---------- project_type ----------
COMMENT ON COLUMN project_type.id          IS '项目类型 ID';
COMMENT ON COLUMN project_type.code        IS '类型编码: DELIVERY/SELF_RD/INNER_PRODUCT/RD';
COMMENT ON COLUMN project_type.name        IS '类型名称';
COMMENT ON COLUMN project_type.description IS '类型说明';

-- ---------- project_status ----------
COMMENT ON COLUMN project_status.id         IS '状态 ID';
COMMENT ON COLUMN project_status.code       IS '状态编码: DRAFT/PENDING/ACTIVE/SUSPENDED/CLOSED/REJECTED';
COMMENT ON COLUMN project_status.name       IS '状态名称';
COMMENT ON COLUMN project_status.is_terminal IS '是否终态(CLOSED/REJECTED 为 TRUE)';

-- ---------- health_level ----------
COMMENT ON COLUMN health_level.id        IS '健康度 ID';
COMMENT ON COLUMN health_level.code      IS '编码: GREEN/YELLOW/RED';
COMMENT ON COLUMN health_level.name      IS '名称(正常/关注/严重)';
COMMENT ON COLUMN health_level.color_hex IS '前端展示色(#RRGGBB)';

-- ---------- project ----------
COMMENT ON COLUMN project.id              IS '项目 ID';
COMMENT ON COLUMN project.code            IS '业务编号(唯一,如 P-2025-001)';
COMMENT ON COLUMN project.name            IS '项目名称';
COMMENT ON COLUMN project.type_id         IS '项目类型 ID(project_type.id)';
COMMENT ON COLUMN project.status_id       IS '项目状态 ID(project_status.id)';
COMMENT ON COLUMN project.health_id       IS '健康度 ID(可空)';
COMMENT ON COLUMN project.customer        IS '客户名称(交付类必填)';
COMMENT ON COLUMN project.department_id   IS '负责部门 ID';
COMMENT ON COLUMN project.pm_user_id      IS '项目经理 ID(app_user.id)';
COMMENT ON COLUMN project.sponsor_user_id IS '项目发起人 ID';
COMMENT ON COLUMN project.description     IS '项目描述(富文本)';
COMMENT ON COLUMN project.background      IS '项目背景';
COMMENT ON COLUMN project.goals           IS '项目目标';
COMMENT ON COLUMN project.scope           IS '项目范围';
COMMENT ON COLUMN project.plan_start_date IS '计划开始日期';
COMMENT ON COLUMN project.plan_end_date   IS '计划结束日期';
COMMENT ON COLUMN project.actual_start_date IS '实际开始日期';
COMMENT ON COLUMN project.actual_end_date   IS '实际结束日期';
COMMENT ON COLUMN project.plan_workdays   IS '预计人天';
COMMENT ON COLUMN project.progress_pct    IS '项目进度 0-100(由里程碑加权计算)';
COMMENT ON COLUMN project.budget_estimate IS '预计预算(元)';
COMMENT ON COLUMN project.bu_id           IS '业务单元 ID(V2.1)';
COMMENT ON COLUMN project.pl_id           IS '产品线 ID(V2.1)';
COMMENT ON COLUMN project.related_product_id IS '关联产品 ID(V2.1)';
COMMENT ON COLUMN project.created_by      IS '创建人 ID';
COMMENT ON COLUMN project.created_at      IS '创建时间';
COMMENT ON COLUMN project.updated_at      IS '更新时间';
COMMENT ON COLUMN project.deleted         IS '软删标记';

-- ---------- initiation_status ----------
COMMENT ON COLUMN initiation_status.id          IS '立项状态 ID';
COMMENT ON COLUMN initiation_status.code        IS '编码: DRAFT/PENDING/DEPT_APPROVED/PMO_APPROVED/EXEC_APPROVED/REJECTED/SUPPLEMENT';
COMMENT ON COLUMN initiation_status.name        IS '名称';
COMMENT ON COLUMN initiation_status.sort_order  IS '排序号';
COMMENT ON COLUMN initiation_status.is_terminal IS '是否终态';

-- ---------- project_initiation ----------
COMMENT ON COLUMN project_initiation.id            IS '立项申请 ID';
COMMENT ON COLUMN project_initiation.code          IS '立项编号(唯一,如 IR-2025-001)';
COMMENT ON COLUMN project_initiation.project_id    IS '批准后回写的项目 ID(可空,批准时创建)';
COMMENT ON COLUMN project_initiation.title         IS '立项标题(冗余项目名,便于列表展示)';
COMMENT ON COLUMN project_initiation.applicant_id  IS '申请人 ID';
COMMENT ON COLUMN project_initiation.department_id IS '申请部门';
COMMENT ON COLUMN project_initiation.background    IS '背景说明';
COMMENT ON COLUMN project_initiation.goals         IS '目标说明';
COMMENT ON COLUMN project_initiation.scope         IS '范围说明';
COMMENT ON COLUMN project_initiation.plan_workdays IS '预计人天';
COMMENT ON COLUMN project_initiation.budget_estimate IS '预计预算';
COMMENT ON COLUMN project_initiation.planned_start IS '计划开始';
COMMENT ON COLUMN project_initiation.planned_end   IS '计划结束';
COMMENT ON COLUMN project_initiation.initial_risks IS '初始风险';
COMMENT ON COLUMN project_initiation.status_id     IS '当前状态 ID(initiation_status.id)';
COMMENT ON COLUMN project_initiation.current_step  IS '当前审批步骤: DEPT/PMO/EXEC';
COMMENT ON COLUMN project_initiation.submitted_at  IS '首次提交时间';
COMMENT ON COLUMN project_initiation.closed_at     IS '审批关闭时间(通过或驳回)';
COMMENT ON COLUMN project_initiation.created_at    IS '创建时间';
COMMENT ON COLUMN project_initiation.updated_at    IS '更新时间';
COMMENT ON COLUMN project_initiation.deleted       IS '软删标记';

-- ---------- approval_step ----------
COMMENT ON COLUMN approval_step.id          IS '步骤 ID';
COMMENT ON COLUMN approval_step.code        IS '步骤编码: DEPT_LEAD/PMO_ADMIN/EXEC';
COMMENT ON COLUMN approval_step.name        IS '步骤名称';
COMMENT ON COLUMN approval_step.sequence    IS '顺序号 1/2/3';
COMMENT ON COLUMN approval_step.description IS '步骤说明';

-- ---------- approval_record ----------
COMMENT ON COLUMN approval_record.id             IS '审批记录 ID';
COMMENT ON COLUMN approval_record.initiation_id  IS '立项 ID';
COMMENT ON COLUMN approval_record.step_id        IS '审批步骤 ID';
COMMENT ON COLUMN approval_record.approver_id    IS '审批人 ID';
COMMENT ON COLUMN approval_record.decision       IS '决定: APPROVED/REJECTED/SUPPLEMENT';
COMMENT ON COLUMN approval_record.comment        IS '审批意见';
COMMENT ON COLUMN approval_record.decided_at     IS '决定时间';
COMMENT ON COLUMN approval_record.created_at     IS '记录创建时间';
COMMENT ON COLUMN approval_record.on_behalf_of_user_id IS '代审关系(V1.8 P1.5):审批人实际代表谁';

-- ---------- milestone_status ----------
COMMENT ON COLUMN milestone_status.id          IS '里程碑状态 ID';
COMMENT ON COLUMN milestone_status.code        IS '编码: PENDING/IN_PROGRESS/COMPLETED/DELAYED';
COMMENT ON COLUMN milestone_status.name        IS '名称';
COMMENT ON COLUMN milestone_status.is_terminal IS '是否终态';

-- ---------- milestone ----------
COMMENT ON COLUMN milestone.id            IS '里程碑 ID';
COMMENT ON COLUMN milestone.project_id    IS '所属项目 ID';
COMMENT ON COLUMN milestone.name          IS '里程碑名称';
COMMENT ON COLUMN milestone.sequence      IS '项目内序号(同项目内唯一)';
COMMENT ON COLUMN milestone.plan_date     IS '计划完成日期';
COMMENT ON COLUMN milestone.actual_date   IS '实际完成日期';
COMMENT ON COLUMN milestone.status_id     IS '状态 ID';
COMMENT ON COLUMN milestone.weight        IS '权重 1-10(用于加权进度计算)';
COMMENT ON COLUMN milestone.owner_user_id IS '负责人 ID';
COMMENT ON COLUMN milestone.deliverable   IS '交付物说明';
COMMENT ON COLUMN milestone.remark        IS '备注';
COMMENT ON COLUMN milestone.completed_at  IS '实际完成时间';
COMMENT ON COLUMN milestone.created_at    IS '创建时间';
COMMENT ON COLUMN milestone.updated_at    IS '更新时间';
COMMENT ON COLUMN milestone.deleted       IS '软删标记';

-- ---------- operation_log ----------
COMMENT ON COLUMN operation_log.id            IS '日志 ID';
COMMENT ON COLUMN operation_log.user_id       IS '操作人 ID';
COMMENT ON COLUMN operation_log.resource_type IS '资源类型: PROJECT/INITIATION/MILESTONE/USER';
COMMENT ON COLUMN operation_log.resource_id   IS '资源 ID';
COMMENT ON COLUMN operation_log.action        IS '操作: CREATE/UPDATE/DELETE/APPROVE/REJECT';
COMMENT ON COLUMN operation_log.payload       IS '变更前后快照(JSONB)';
COMMENT ON COLUMN operation_log.ip_address    IS '来源 IP';
COMMENT ON COLUMN operation_log.created_at    IS '操作时间';

-- ---------- revoked_token ----------
COMMENT ON COLUMN revoked_token.id         IS '记录 ID';
COMMENT ON COLUMN revoked_token.jti        IS 'JWT ID(唯一)';
COMMENT ON COLUMN revoked_token.user_id    IS '被撤销的用户 ID';
COMMENT ON COLUMN revoked_token.revoked_at IS '撤销时间';
COMMENT ON COLUMN revoked_token.expires_at IS '原 token 过期时间(用于定时清理)';

-- ---------- timesheet_week ----------
COMMENT ON COLUMN timesheet_week.id            IS '工时周报 ID';
COMMENT ON COLUMN timesheet_week.user_id       IS '填报人';
COMMENT ON COLUMN timesheet_week.week_start    IS '周开始日期(周一)';
COMMENT ON COLUMN timesheet_week.week_end      IS '周结束日期(周日)';
COMMENT ON COLUMN timesheet_week.status        IS '状态: DRAFT/SUBMITTED/APPROVED/REJECTED';
COMMENT ON COLUMN timesheet_week.submitter_note IS '填报备注';
COMMENT ON COLUMN timesheet_week.submitted_at  IS '提交时间';
COMMENT ON COLUMN timesheet_week.approver_id   IS '审批人 ID';
COMMENT ON COLUMN timesheet_week.approved_at   IS '审批时间';
COMMENT ON COLUMN timesheet_week.created_at    IS '创建时间';
COMMENT ON COLUMN timesheet_week.updated_at    IS '更新时间';
COMMENT ON COLUMN timesheet_week.deleted       IS '软删标记';

-- ---------- timesheet_entry ----------
COMMENT ON COLUMN timesheet_entry.id           IS '工时明细 ID';
COMMENT ON COLUMN timesheet_entry.timesheet_id IS '所属周报 ID';
COMMENT ON COLUMN timesheet_entry.work_date    IS '工作日期';
COMMENT ON COLUMN timesheet_entry.project_id   IS '项目 ID';
COMMENT ON COLUMN timesheet_entry.milestone_id IS '里程碑 ID(可空)';
COMMENT ON COLUMN timesheet_entry.hours        IS '工时(小时,0-24)';
COMMENT ON COLUMN timesheet_entry.description  IS '工作内容描述';
COMMENT ON COLUMN timesheet_entry.created_at   IS '创建时间';
COMMENT ON COLUMN timesheet_entry.updated_at   IS '更新时间';
COMMENT ON COLUMN timesheet_entry.deleted      IS '软删标记';

-- ---------- notification ----------
COMMENT ON COLUMN notification.id            IS '通知 ID';
COMMENT ON COLUMN notification.recipient_id  IS '收件人 ID(逻辑关联 app_user,不强 FK)';
COMMENT ON COLUMN notification.category      IS '通知分类';
COMMENT ON COLUMN notification.resource_id   IS '关联资源 ID';
COMMENT ON COLUMN notification.resource_code IS '关联资源编号';
COMMENT ON COLUMN notification.title         IS '通知标题';
COMMENT ON COLUMN notification.content       IS '通知内容';
COMMENT ON COLUMN notification.status        IS '状态: UNREAD/READ/ARCHIVED';
COMMENT ON COLUMN notification.read_at       IS '已读时间';
COMMENT ON COLUMN notification.created_at    IS '创建时间';

-- ---------- user_im_binding ----------
COMMENT ON COLUMN user_im_binding.id              IS '绑定 ID';
COMMENT ON COLUMN user_im_binding.user_id         IS '用户 ID';
COMMENT ON COLUMN user_im_binding.channel         IS 'IM 平台: wechat_work/dingtalk/feishu';
COMMENT ON COLUMN user_im_binding.external_user_id IS 'IM 平台用户标识(企微 userid / 钉钉邮箱 / 飞书邮箱)';
COMMENT ON COLUMN user_im_binding.enabled         IS '是否启用';
COMMENT ON COLUMN user_im_binding.created_at      IS '创建时间';
COMMENT ON COLUMN user_im_binding.updated_at      IS '更新时间';

-- ---------- user_im_quiet_hours ----------
COMMENT ON COLUMN user_im_quiet_hours.id         IS '勿扰时段 ID';
COMMENT ON COLUMN user_im_quiet_hours.user_id    IS '用户 ID';
COMMENT ON COLUMN user_im_quiet_hours.start_time IS '起始时间(HH:mm)';
COMMENT ON COLUMN user_im_quiet_hours.end_time   IS '结束时间(HH:mm)';
COMMENT ON COLUMN user_im_quiet_hours.timezone   IS '时区(默认 Asia/Shanghai)';
COMMENT ON COLUMN user_im_quiet_hours.enabled    IS '是否启用';
COMMENT ON COLUMN user_im_quiet_hours.created_at IS '创建时间';
COMMENT ON COLUMN user_im_quiet_hours.updated_at IS '更新时间';

-- ---------- business_unit ----------
COMMENT ON COLUMN business_unit.id          IS 'BU ID';
COMMENT ON COLUMN business_unit.code        IS 'BU 编码(唯一,如 FIN/GOV)';
COMMENT ON COLUMN business_unit.name        IS 'BU 名称';
COMMENT ON COLUMN business_unit.description IS 'BU 说明';
COMMENT ON COLUMN business_unit.sort_order  IS '排序号';
COMMENT ON COLUMN business_unit.enabled     IS '是否启用';
COMMENT ON COLUMN business_unit.created_at  IS '创建时间';
COMMENT ON COLUMN business_unit.updated_at  IS '更新时间';
COMMENT ON COLUMN business_unit.deleted     IS '软删标记';

-- ---------- product_line ----------
COMMENT ON COLUMN product_line.id          IS 'PL ID';
COMMENT ON COLUMN product_line.bu_id       IS '所属 BU ID';
COMMENT ON COLUMN product_line.code        IS 'PL 编码(唯一,如 FIN-PAY)';
COMMENT ON COLUMN product_line.name        IS 'PL 名称';
COMMENT ON COLUMN product_line.description IS 'PL 说明';
COMMENT ON COLUMN product_line.sort_order  IS '排序号';
COMMENT ON COLUMN product_line.enabled     IS '是否启用';
COMMENT ON COLUMN product_line.created_at  IS '创建时间';
COMMENT ON COLUMN product_line.updated_at  IS '更新时间';
COMMENT ON COLUMN product_line.deleted     IS '软删标记';

-- ---------- related_product ----------
COMMENT ON COLUMN related_product.id          IS '关联产品 ID';
COMMENT ON COLUMN related_product.pl_id       IS '所属 PL ID';
COMMENT ON COLUMN related_product.code        IS '产品编码(唯一)';
COMMENT ON COLUMN related_product.name        IS '产品名称';
COMMENT ON COLUMN related_product.description IS '产品说明';
COMMENT ON COLUMN related_product.version     IS '产品版本(可空)';
COMMENT ON COLUMN related_product.sort_order  IS '排序号';
COMMENT ON COLUMN related_product.enabled     IS '是否启用';
COMMENT ON COLUMN related_product.created_at  IS '创建时间';
COMMENT ON COLUMN related_product.updated_at  IS '更新时间';
COMMENT ON COLUMN related_product.deleted     IS '软删标记';
