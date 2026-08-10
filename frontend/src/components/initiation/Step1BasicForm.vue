<template>
  <!-- Step 1 — 立项基础信息 + 铁三角 (AR/SR/FR) + 客户名 + 合同金额 + 基础信息补全 (V4.17) -->
  <el-form :model="form" label-width="120px" :disabled="disabled">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="立项编号" required>
          <el-input v-model="form.code" placeholder="自动生成 / 手动指定" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="立项标题" required>
          <el-input v-model="form.title" placeholder="一句话说清要做什么" />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-form-item label="客户名称">
          <el-input v-model="form.clientName" placeholder="客户公司全称" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="客户联系人">
          <el-input v-model="form.clientContactName" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="客户电话">
          <el-input v-model="form.clientContactPhone" />
        </el-form-item>
      </el-col>
    </el-row>

    <el-divider content-position="left">铁三角(AR / SR / FR)</el-divider>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-form-item label="客户经理 AR">
          <el-input v-model="form.arUserName" placeholder="工号或姓名">
            <template #prepend>AR</template>
          </el-input>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="售前 SR">
          <el-input v-model="form.srUserName" placeholder="工号或姓名">
            <template #prepend>SR</template>
          </el-input>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="方案经理 FR">
          <el-input v-model="form.frUserName" placeholder="工号或姓名(承接项目者)">
            <template #prepend>FR</template>
          </el-input>
        </el-form-item>
      </el-col>
    </el-row>

    <el-divider content-position="left">合同与商务</el-divider>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-form-item label="合同金额">
          <el-input-number
            v-model="form.contractAmount"
            :min="0"
            :step="10000"
            :precision="2"
            style="width: 100%"
            controls-position="right"
          />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="币种">
          <el-select v-model="form.contractCurrency" style="width: 100%">
            <el-option label="CNY 人民币" value="CNY" />
            <el-option label="USD 美元" value="USD" />
            <el-option label="EUR 欧元" value="EUR" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="计划工期(周)">
          <el-input-number v-model="form.planWorkWeeks" :min="1" :max="104" style="width: 100%" />
        </el-form-item>
      </el-col>
    </el-row>

    <!-- ==================== V4.17 立项基础信息补全 ==================== -->
    <el-divider content-position="left">基础信息(V4.17)</el-divider>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="所属部门">
          <el-tree-select
            v-model="form.departmentId"
            :data="departments"
            :props="deptTreeProps"
            node-key="id"
            check-strictly
            clearable
            placeholder="选择承接部门"
            style="width: 100%"
            :render-after-expand="false"
            default-expand-all
          />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="项目经理 PM">
          <el-select
            v-model="form.pmUserId"
            filterable
            clearable
            placeholder="选择项目经理 (默认 = 申请人)"
            style="width: 100%"
          >
            <el-option
              v-for="u in pmCandidates"
              :key="u.id"
              :label="`${u.fullName} (${u.username})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-form-item label="项目类型">
          <el-select v-model="form.projectTypeCode" clearable style="width: 100%">
            <el-option
              v-for="t in projectTypes"
              :key="t.code"
              :label="`${t.name} (${t.code})`"
              :value="t.code"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="项目级别">
          <el-select v-model="form.projectLevelCode" clearable style="width: 100%">
            <el-option
              v-for="l in projectLevels"
              :key="l.code"
              :label="`${l.name} (${l.code})`"
              :value="l.code"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="预估毛利率 (%)">
          <el-input-number
            v-model="form.expectedGrossMarginPct"
            :min="0"
            :max="100"
            :step="1"
            :precision="2"
            style="width: 100%"
            controls-position="right"
            placeholder="0~100"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-form-item label="入场时间">
          <el-date-picker
            v-model="form.plannedStart"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="kickoff 日期"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="项目结束时间">
          <el-date-picker
            v-model="form.plannedEnd"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="项目验收目标日"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="计划上线时间">
          <el-date-picker
            v-model="form.plannedLaunchDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="UAT/灰度→全量目标日"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
    </el-row>
    <!-- ==================== /V4.17 立项基础信息补全 ==================== -->

    <el-divider content-position="left">立项内容</el-divider>

    <el-form-item label="背景">
      <el-input
        v-model="form.background"
        type="textarea"
        :rows="2"
        placeholder="为什么要做这个项目 / 解决什么业务问题"
      />
    </el-form-item>
    <el-form-item label="目标">
      <el-input v-model="form.goals" type="textarea" :rows="2" placeholder="可衡量的成功标准 (KPI / OKR)" />
    </el-form-item>
    <el-form-item label="范围">
      <el-input v-model="form.scope" type="textarea" :rows="2" placeholder="做什么 / 不做什么 (含/不含)" />
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'

/** 部门节点 (轻量, 仅取 Step 1 需要的字段) */
export interface DepartmentLite {
  id: number
  name: string
  parentId?: number | null
  children?: DepartmentLite[]
}

/** 项目类型字典 */
export interface ProjectTypeOption {
  id: number
  code: string
  name: string
}

/** 项目级别字典 */
export interface ProjectLevelOption {
  id: number
  code: string
  name: string
  sortOrder?: number
}

/** PM 候选人 (从 /users 取, 取 primaryRole.code === 'PM' 的) */
export interface PmCandidate {
  id: number
  username: string
  fullName: string
  primaryRole?: { id: number; code: string; name: string } | null
}

export interface InitiationBasicForm {
  code: string
  title: string
  clientName?: string
  clientContactName?: string
  clientContactPhone?: string
  arUserName?: string
  srUserName?: string
  frUserName?: string
  contractAmount?: number
  contractCurrency?: string
  planWorkWeeks?: number
  background?: string
  goals?: string
  scope?: string
  // V4.17 立项基础信息补全
  /** 所属部门 id */
  departmentId?: number
  /** 项目经理 user id */
  pmUserId?: number
  /** 项目类型 code (DELIVERY/SELF_RD/INNER_PRODUCT/RD) */
  projectTypeCode?: string
  /** 项目级别 code (S/A/B/C) */
  projectLevelCode?: string
  /** 预估毛利率 %, 0~100 */
  expectedGrossMarginPct?: number
  /** 入场时间 (kickoff) */
  plannedStart?: string
  /** 项目结束时间 */
  plannedEnd?: string
  /** 计划上线时间 (UAT/灰度→全量目标日) */
  plannedLaunchDate?: string
}

defineProps({
  form: {
    type: Object as PropType<InitiationBasicForm>,
    required: true,
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  /** 部门树 (从 /departments/tree 拉) */
  departments: {
    type: Array as PropType<DepartmentLite[]>,
    default: () => [],
  },
  /** 项目类型字典 (从 /dict/project-types 拉) */
  projectTypes: {
    type: Array as PropType<ProjectTypeOption[]>,
    default: () => [],
  },
  /** 项目级别字典 (从 /dict/project-levels 拉) */
  projectLevels: {
    type: Array as PropType<ProjectLevelOption[]>,
    default: () => [],
  },
  /** PM 候选人 (从 /users 拉, 过滤 primaryRole.code === 'PM') */
  pmCandidates: {
    type: Array as PropType<PmCandidate[]>,
    default: () => [],
  },
})

const deptTreeProps = {
  value: 'id',
  label: 'name',
  children: 'children',
}
</script>
