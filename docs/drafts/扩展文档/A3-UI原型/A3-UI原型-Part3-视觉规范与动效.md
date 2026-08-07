# A3 UI/UX 原型 Part3 — 视觉规范(Design Token)与交互动效

> 本 Part 覆盖:A3.4 视觉规范(颜色/字体/间距/圆角/阴影/动效曲线)/ A3.5 组件库 spec / A3.6 交互动效细则 / A3.7 无障碍(Accessibility)/ A3.8 实施 Checklist。
> Part1 已完成信息架构,Part2A/2B 已完成 8 个核心页 wireframe;本 Part 给出可被前端"按 token 引用"的视觉规范。

---

## A3.4 视觉规范(Design Token)

> 所有视觉值通过 CSS 变量 + 主题文件(`frontend/src/styles/tokens.scss` 或 Tailwind config)统一管理,组件不直接写死颜色/字号。

### A3.4.1 颜色规范

#### A3.4.1.1 品牌色(Brand)

| Token | Hex | 用途 | WCAG AA |
| --- | --- | --- | --- |
| `--brand-500` | `#1677ff` | 主按钮 / 链接 / 选中态 | 对白 4.5:1 ✅ |
| `--brand-600` | `#0958d9` | 主按钮 hover | 对白 5.5:1 ✅ |
| `--brand-700` | `#003eb3` | 主按钮 active | 对白 8:1 ✅ |
| `--brand-50`  | `#e6f4ff` | 选中背景 / 高亮行 | 文本 4.5:1 ✅ |
| `--brand-100` | `#bae0ff` | tag-bg / chip 浅 | 文本 4.5:1 ✅ |

> **规则**:主操作 1 个页面 ≤ 3 个品牌色填充;其他用功能色。

#### A3.4.1.2 功能色 — 状态(Semantic)

| 状态 | Token | Hex | 浅色背景 | 文字色 |
| --- | --- | --- | --- | --- |
| 成功 / 通过 | `--success-500` | `#52c41a` | `#f6ffed` | `#389e0d` |
| 警告 / 临近 | `--warning-500` | `#faad14` | `#fffbe6` | `#d48806` |
| 错误 / 失败 | `--error-500`   | `#ff4d4f` | `#fff1f0` | `#cf1322` |
| 信息 | `--info-500`    | `#1677ff` | `#e6f4ff` | `#0958d9` |
| 草稿 | `--neutral-500` | `#8c8c8c` | `#fafafa` | `#595959` |

> **RAG 三色**(项目健康度专用):
> - 绿 `G` = `--success-500` (进度 ≥ 计划 + 健康度 100% ~ 80%)
> - 黄 `A` = `--warning-500` (进度 -10% ~ -20% 偏离 或 健康度 60% ~ 80%)
> - 红 `R` = `--error-500`   (进度 > 20% 偏离 或 健康度 < 60%)

#### A3.4.1.3 中性色(Neutral / Text / Border / Background)

| Token | Hex | 用途 |
| --- | --- | --- |
| `--text-primary`   | `#1f1f1f` | 主文本(标题/正文) |
| `--text-secondary` | `#595959` | 次文本(描述/标签) |
| `--text-tertiary`  | `#8c8c8c` | 弱化文本(占位/辅助) |
| `--text-disabled`  | `#bfbfbf` | 禁用态 |
| `--text-inverse`   | `#ffffff` | 深底白字 |
| `--border-default` | `#d9d9d9` | 常规边框 |
| `--border-light`   | `#f0f0f0` | 表格分隔线 |
| `--border-strong`  | `#8c8c8c` | 输入框 hover |
| `--bg-base`        | `#ffffff` | 卡片底 |
| `--bg-elevated`    | `#ffffff` | 弹层/抽屉 |
| `--bg-mask`        | `rgba(0,0,0,0.45)` | 模态遮罩 |
| `--bg-layout`      | `#f5f5f5` | 整体背景 |

#### A3.4.1.4 数据可视化色板(Chart)

> 用于甘特图 / 燃尽图 / 资源矩阵 / 风险热力图。
> 8 色饱和度一致(避免视觉抖动):

| Token | Hex | 用法 |
| --- | --- | --- |
| `--chart-1` | `#1677ff` | 主数据 / 计划 |
| `--chart-2` | `#52c41a` | 实际 / 完成 |
| `--chart-3` | `#faad14` | 偏差 / 警告 |
| `--chart-4` | `#722ed1` | 第四系列 |
| `--chart-5` | `#13c2c2` | 第五系列 |
| `--chart-6` | `#eb2f96` | 第六系列 |
| `--chart-7` | `#fa8c16` | 第七系列 |
| `--chart-8` | `#a0d911` | 第八系列 |

#### A3.4.1.5 暗色模式(Dark Mode)预留

> V1 不实现,V2 实施。预留 token 命名,前端可统一切换:

```scss
[data-theme='dark'] {
  --text-primary: rgba(255, 255, 255, 0.85);
  --bg-base: #141414;
  --border-default: #303030;
  // ...
}
```

---

### A3.4.2 字体规范

#### A3.4.2.1 字体族

| Token | 值 | 用途 |
| --- | --- | --- |
| `--font-sans` | `"PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif` | 全部中文/UI |
| `--font-mono` | `"JetBrains Mono", "SF Mono", "Consolas", monospace` | 代码 / 等宽数字 / 进度% |
| `--font-num`  | `"DIN Alternate", "Helvetica Neue", monospace` | 大数字(仪表盘 KPI) |

> **加载策略**:不引外部字体(避免 FOIT/FOUT 与字体盗链问题);系统字体足够。

#### A3.4.2.2 字号阶梯(Type Scale)

| Token | px / rem | 行高 | 字重 | 用途 |
| --- | --- | --- | --- | --- |
| `--fs-display-1` | 32 / 2.0 | 1.2 | 600 | 仪表盘 KPI 大数字 |
| `--fs-h1`        | 24 / 1.5 | 1.33 | 600 | 页主标题 |
| `--fs-h2`        | 20 / 1.25 | 1.4 | 600 | 卡片标题 |
| `--fs-h3`        | 16 / 1.0 | 1.5 | 600 | 段落标题 |
| `--fs-body`      | 14 / 0.875 | 1.57 | 400 | 正文 |
| `--fs-body-sm`   | 13 / 0.8125 | 1.54 | 400 | 次要正文 |
| `--fs-caption`   | 12 / 0.75 | 1.5 | 400 | 注释 / 占位 |
| `--fs-micro`     | 11 / 0.6875 | 1.45 | 400 | 表格辅助 / 时间 |

> **规则**:全站字号跨度 5 ~ 7 档即可,不要超过 9 档。

#### A3.4.2.3 数字字重

| 场景 | 字重 | 例子 |
| --- | --- | --- |
| 关键 KPI(项目数 / 进度) | 600 | `35` |
| 表格内数字 | 400 | `1,234.56` |
| 等宽数据(版本号 / 工时) | 500 mono | `v2.3.1` |

---

### A3.4.3 间距规范(8 进制栅格)

> **基础单位**:`4px`(8 进制 = 4 / 8 / 12 / 16 / 24 / 32 / 48 / 64)。
> **规则**:所有 margin / padding / gap 必须从以下 token 取,**禁止**写 `13px`、`17px` 等非栅格值。

| Token | px | 用途 |
| --- | --- | --- |
| `--space-0`  | 0 | 占位 |
| `--space-1`  | 4  | 图标内距 / tag 上下 |
| `--space-2`  | 8  | 表单 label 与控件 / 文字行内 gap |
| `--space-3`  | 12 | 按钮内距 / 卡片内字段间距 |
| `--space-4`  | 16 | 卡片内边距 / 段落间距 |
| `--space-5`  | 24 | 卡片间 gap / 表单 field 间距 |
| `--space-6`  | 32 | 区块间 gap |
| `--space-7`  | 48 | 大区块分隔(列表与详情) |
| `--space-8`  | 64 | 页面首屏边距 |
| `--space-9`  | 96 | 引导页 / 营销页 |

#### A3.4.3.1 常用组合速查

| 场景 | 组合 |
| --- | --- |
| 按钮 padding | `8px 16px`(--space-2 vertical / --space-4 horizontal) |
| 卡片 padding | `16px` (--space-4) |
| 卡片间 gap | `16px` (--space-4) / 大型用 `24px` (--space-5) |
| 表单 field 间距 | `24px` (--space-5) |
| 段落间距 | `12px` (--space-3) |
| 列表项垂直 padding | `12px` (--space-3) |
| 弹层 padding | `24px` (--space-5) |
| 弹层/抽屉与视口边距 | `32px` (--space-6) |

### A3.4.4 圆角规范

| Token | px | 用途 |
| --- | --- | --- |
| `--radius-none` | 0 | 表格单元格 |
| `--radius-sm`   | 2 | 小 tag / badge |
| `--radius-md`   | 4 | 按钮 / 输入框 / tag |
| `--radius-lg`   | 8 | 卡片 / 弹层 |
| `--radius-xl`   | 12 | 抽屉头部 / 大弹层 |
| `--radius-full` | 9999px | 头像 / 圆形按钮 / 进度条 |

> **规则**:同页面圆角跨级 ≤ 2 档(如页面有 4 + 8,不要再出现 12)。

### A3.4.5 阴影规范

> **设计目标**:层级清晰,避免过度阴影。普通页面最多 2 档阴影,弹层 1 档,悬浮态 1 档。

| Token | 值 | 用途 |
| --- | --- | --- |
| `--shadow-0` | none | 默认平面 |
| `--shadow-1` | `0 1px 2px rgba(0,0,0,.03), 0 1px 6px rgba(0,0,0,.04)` | 卡片 |
| `--shadow-2` | `0 6px 16px rgba(0,0,0,.08), 0 3px 6px rgba(0,0,0,.04)` | 弹层 / 抽屉 / 悬浮 |
| `--shadow-3` | `0 9px 28px rgba(0,0,0,.09), 0 6px 16px rgba(0,0,0,.06)` | 强弹层(确认/对话框) |
| `--shadow-inset` | `inset 0 2px 4px rgba(0,0,0,.06)` | 输入框 active / 按钮 pressed |

### A3.4.6 动效曲线

> **设计目标**:快 + 自然。任何过渡 ≤ 300ms,常用 150ms / 200ms / 250ms 三档。

| Token | 曲线 | 时长 | 用途 |
| --- | --- | --- | --- |
| `--motion-fast`   | `cubic-bezier(0.4, 0, 0.2, 1)` | 150ms | hover / focus / 微小状态 |
| `--motion-base`   | `cubic-bezier(0.4, 0, 0.2, 1)` | 200ms | 颜色 / 边框 / 文本过渡 |
| `--motion-slow`   | `cubic-bezier(0.4, 0, 0.2, 1)` | 250ms | 弹层 / 抽屉 / 模态 |
| `--motion-screen` | `cubic-bezier(0.4, 0, 0.2, 1)` | 300ms | 路由切换 / 大型内容 |
| `--ease-out`      | `cubic-bezier(0.0, 0, 0.2, 1)` | — | 元素离开 |
| `--ease-in`       | `cubic-bezier(0.4, 0, 1, 1)`   | — | 元素进入(谨慎) |
| `--ease-in-out`   | `cubic-bezier(0.4, 0, 0.2, 1)` | — | 循环 / 进度 |

> **规则**:
> - 同一元素的多个属性过渡,**统一曲线**(避免颜色用 base、位移用 ease-in)。
> - 路由切换用 `<transition name="fade">` 250ms。
> - **禁止**超过 500ms 的过渡(会感知卡顿)。
> - 用户开启 `prefers-reduced-motion: reduce` 时,全局 `transition-duration: 0.01ms !important`。


---

## A3.5 组件库规范(Component Spec)

> 已基于 Element Plus 2.x 二次封装于 `frontend/src/components/ui/`。
> 每个组件 spec 包含:**props / 变体(variant)/ 尺寸(size)/ 状态(state)/ 间距/ 可访问性**。

### A3.5.1 Button 按钮

#### Props

```ts
interface ButtonProps {
  variant?: 'primary' | 'default' | 'dashed' | 'link' | 'text' | 'danger'  // 默认 primary
  size?: 'sm' | 'md' | 'lg'  // 默认 md (32px 高)
  shape?: 'default' | 'circle' | 'round'
  loading?: boolean
  disabled?: boolean
  block?: boolean
  icon?: Component  // Element Plus Icon
  iconPosition?: 'left' | 'right'
  permission?: string  // RBAC 二次封装,例:'APPROVE'
}
```

#### 视觉规则

| Variant | 背景 | 边框 | 文字 | Hover | Active |
| --- | --- | --- | --- | --- | --- |
| primary | `--brand-500` | none | `#fff` | `--brand-600` | `--brand-700` |
| default | `#fff` | `--border-default` | `--text-primary` | `--brand-50` 边框 | `--brand-600` 文字 |
| dashed | `#fff` | `--border-default` dashed | `--text-primary` | 同 default | 同 default |
| danger | `--error-500` | none | `#fff` | `#ff7875` | `#d9363e` |
| link | none | none | `--brand-500` | `--brand-600` | `--brand-700` |
| text | none | none | `--text-primary` | `--bg-mask` 5% | `--bg-mask` 10% |

| Size | 高 | 字号 | padding |
| --- | --- | --- | --- |
| sm | 24px | 12px | 0 12px |
| md | 32px | 14px | 0 16px |
| lg | 40px | 16px | 0 20px |

#### 状态

- `loading`:显示旋转图标,文字保持(可读),按钮 disabled。
- `disabled`:背景 `--bg-layout`,文字 `--text-disabled`,无 hover。
- **禁止**把 disabled 文字色用 `--text-tertiary`(对比不够)。

### A3.5.2 Tag / Badge 标签

#### 视觉规则

| 类别 | 浅色背景 | 文字 | 用途 |
| --- | --- | --- | --- |
| success | `#f6ffed` | `#389e0d` | 已完成 / 通过 |
| warning | `#fffbe6` | `#d48806` | 即将到期 / 风险中 |
| error | `#fff1f0` | `#cf1322` | 失败 / 阻塞 |
| info | `#e6f4ff` | `#0958d9` | 信息 / 进行中 |
| neutral | `#fafafa` | `#595959` | 草稿 / 归档 |

> **规则**:
> - 一个 tag 必须能"独立阅读"——文本 4.5:1,背景 3:1。
> - 通知类型(立项提交/审批决定/补料重提)必须用不同色:info / success / warning。

### A3.5.3 Input / Form 表单

#### 视觉规则

- **高 32px**,`--radius-md`(4px)。
- 边框 `--border-default`,hover `--border-strong`,focus `--brand-500`(2px outline)。
- 错误状态边框 `--error-500`,下方红色 helper 文字(字号 `--fs-caption`)。
- Label 字号 `--fs-body`,色 `--text-secondary`,**必填红色星号**。
- 表单 field 间距 `--space-5`(24px)。
- 校验失败时**滚动到第一个错误** + focus。

#### 可访问性

- 每个 input 关联 `<label for>` 或 `aria-label`。
- 错误信息 `aria-describedby` 关联,屏幕阅读器可读。
- 必填 `aria-required="true"`。

### A3.5.4 Modal / Drawer 弹层

| 类型 | 宽度 | 高度 | 动效 |
| --- | --- | --- | --- |
| Modal 小(确认) | 416px | auto | fade + scale(0.95→1) |
| Modal 中(表单) | 560px | auto | fade + scale |
| Modal 大(详情) | 800px | auto | fade + scale |
| Drawer 右侧 | 480px(常规)/ 720px(详情) | 100vh | slide-right |

#### 视觉规则

- 头部:背景 `#fff`,底部 1px 边框 `--border-light`,padding `16px 24px`。
- 内容区:padding `24px`,可滚动区 max-height `calc(100vh - 200px)`。
- 底部:背景 `--bg-layout`,按钮右对齐,主按钮在左,次按钮在右,间距 `8px`。
- 遮罩 `--bg-mask`,点击**不关闭**(防误触,只允许显式取消)。

#### 可访问性

- 焦点陷阱(focus trap),打开时焦点进弹层,关闭时返回触发元素。
- `Esc` 关闭(非破坏性弹层)。
- `role="dialog"` + `aria-modal="true"` + `aria-labelledby` 关联标题。

### A3.5.5 Table 表格

| 元素 | 规格 |
| --- | --- |
| 表头高 | 40px,背景 `--bg-layout`,字重 600 |
| 行高 | 48px(常规)/ 32px(紧凑) |
| 斑马纹 | 关闭(避免视觉噪声,改用 hover 背景) |
| Hover 背景 | `--brand-50` |
| 选中行背景 | `--brand-50` + 左侧 2px 蓝条 |
| 边框 | 单元格之间用 `--border-light`,无外框 |
| 排序 | 箭头 12px,hover 表头变色 |
| 分页器 | 底部右侧,size 切换 10/20/50 |
| 空状态 | 居中插画 + 提示语 + 主操作按钮 |

> **响应式**:
> - 桌面 ≥ 1200px:全部列展示。
> - 平板 768-1199px:隐藏次要列(描述 / 备注)。
> - 移动 < 768px:改为卡片列表(隐藏表头)。

### A3.5.6 Card 卡片

- 背景 `--bg-base`,边框 1px `--border-light`,圆角 `--radius-lg`(8px)。
- 阴影 `--shadow-1`(默认) / `--shadow-2`(hover)。
- 标题栏:padding `16px`,底部 1px 边框,可配操作按钮(右对齐)。
- 内容区:padding `16px` / `24px`。

### A3.5.7 Toast / Notification 轻提示

| 类型 | 图标色 | 时长 |
| --- | --- | --- |
| success | `--success-500` | 3s |
| warning | `--warning-500` | 4s |
| error | `--error-500` | 5s(可手动关闭) |
| info | `--info-500` | 3s |

> **位置**:顶部居中(全局)/ 右下角(操作反馈)。
> **可堆叠**:最多 3 个同时显示,超出排队。



---

## A3.6 交互动效细则(Interaction Spec)

### A3.6.1 Hover

| 元素 | 动效 | 时长 |
| --- | --- | --- |
| 按钮 | 背景色过渡 | 150ms |
| 链接 | 下划线显形 | 150ms |
| 表格行 | 背景色过渡 | 200ms |
| 卡片 | 阴影从 `--shadow-1` 到 `--shadow-2` | 200ms |
| 菜单项 | 背景 + 文字色 | 150ms |

> **规则**:**永远不要用 transform: scale() 做 hover**(会让用户感知"抖动")。

### A3.6.2 Focus

- 键盘 tab 进入控件,2px 蓝 outline(`--brand-500`),偏移 2px。
- **不能移除** `:focus-visible`(无障碍硬要求)。
- 自定义控件(div 模拟 button)必须 `tabindex="0"` + `role` + keydown(Enter/Space)。

### A3.6.3 Click / Press

| 元素 | 动效 |
| --- | --- |
| 按钮 | scale(0.97) + 背景加深,持续 100ms |
| 卡片 | 无 scale(只改 shadow) |
| Checkbox / Radio | 中心填充动画 150ms |

### A3.6.4 Loading

| 场景 | 反馈 |
| --- | --- |
| 按钮提交 | 按钮内置 spinner,disabled 状态 |
| 整页加载 | 顶部 2px 进度条(`--brand-500`) |
| 列表加载 | 骨架屏 3-5 行(用 `--bg-layout` 背景) |
| 表格刷新 | 保留旧数据 + 右上角 spinner |
| 模态 | 居中 spinner + "加载中" |

> **规则**:**禁止**用 alert / 全屏白色遮罩(用户不知进度)。

### A3.6.5 Empty / Error / 404

| 状态 | 视觉 | 文案 |
| --- | --- | --- |
| 空数据 | 灰色插画(80×80) | "暂无数据" + 主操作按钮 |
| 网络错误 | 红色插画 | "加载失败,请重试" + 刷新按钮 |
| 404 | 中性插画 | "页面不存在" + 回首页 |
| 403 | 黄色插画 | "您没有权限访问此页面" + 联系管理员 |

### A3.6.6 路由切换

- `<transition name="page-fade" mode="out-in">` 包裹 `<router-view>`。
- 250ms fade,曲线 `--motion-base`。
- 切换时**保留滚动位置**(back 操作)。

### A3.6.7 列表项增删

- 新增:`<transition-group name="list-fade">` fade-in 200ms。
- 删除:slide-left + fade-out 200ms。
- 拖拽排序:240ms 缓动 + 占位高亮。

### A3.6.8 通知中心抽屉(参考 P1.5 实现)

- 打开:抽屉从右滑入 300ms + 背景遮罩 fade。
- 关闭:反向 200ms。
- 列表项入场:stagger 30ms(每条延迟 30ms,最多 10 条)。

---

## A3.7 无障碍(Accessibility,WCAG 2.1 AA)

### A3.7.1 颜色对比

- **文本**:4.5:1(正文)/ 3:1(大字 ≥ 18px 或粗体 ≥ 14px)。
- **非文本元素**(icon / 边框):3:1。
- 状态色**不能仅靠颜色**传达,必须配图标或文字(如红 + ⚠️)。

### A3.7.2 键盘

- **全部**交互可 tab 到达(自定义控件加 `tabindex`)。
- 焦点顺序与视觉顺序一致。
- `Esc` 关闭弹层/抽屉/菜单。
- `Enter` / `Space` 触发按钮。

### A3.7.3 屏幕阅读器

- 图标按钮:`aria-label="关闭"`,不靠 `title`。
- 表格:`<th scope="col">` / `<th scope="row">`。
- 加载中:`aria-live="polite"` + `aria-busy="true"`。
- 错误:`role="alert"`。

### A3.7.4 缩放 / 字号

- 支持浏览器缩放 200% 不破版。
- 文字不固定 px(用 rem),用户可调。
- 不依赖 hover 才有信息(移动端无 hover)。

### A3.7.5 动画

- 全局监听 `prefers-reduced-motion`,开启后所有动画 → 0.01ms。

### A3.7.6 国际化(i18n)

- 全部文案走 `t('key')` 抽取,不硬编码。
- 预留 RTL(阿拉伯语)布局:`dir="rtl"` 时镜像布局。
- 日期 / 数字 / 货币用 `Intl.NumberFormat` / `Intl.DateTimeFormat`。

---

## A3.8 实施 Checklist(给前端 PR Review 用)

### A3.8.1 颜色

- [ ] 不直接写 `#1677ff` 等具体值,改用 `var(--brand-500)`
- [ ] 不使用不在 token 列表的颜色(避免"野色")
- [ ] 状态色同时配图标(不只靠颜色)

### A3.8.2 字体

- [ ] 字号用 `var(--fs-xxx)`,不写 `14px` / `1rem`
- [ ] 行高 ≥ 1.4(中文)/ 1.5(英文)
- [ ] 中英文混排不串行(用 `--font-sans`)

### A3.8.3 间距

- [ ] 所有 gap / padding 来自 `--space-xxx`
- [ ] 不用 `13px` / `17px` 等非栅格值
- [ ] 卡片间 / 区块间 gap 与本规范一致

### A3.8.4 圆角 / 阴影

- [ ] 跨级 ≤ 2 档
- [ ] 阴影不嵌套(避免"光晕"效果)

### A3.8.5 动效

- [ ] 过渡 ≤ 300ms
- [ ] 不滥用 transform: scale
- [ ] 路由切换有 fade

### A3.8.6 无障碍

- [ ] 自定义控件 `tabindex` + `role` + 键盘事件
- [ ] 弹层焦点陷阱
- [ ] 图标按钮 `aria-label`
- [ ] 颜色对比通过(可用 Chrome DevTools 检)

### A3.8.7 性能

- [ ] 大量列表(>100 行)用虚拟滚动(`el-virtual-list`)
- [ ] 大图懒加载(`loading="lazy"`)
- [ ] 图表按需引入(`echarts` 按模块 import)

---

## 📅 修订记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0 | 2026-06-07 | 首版(随 P1.5 收尾同步) |

> **A3 全部完成(3/3)**:Part1 信息架构 + Part2A/2B 8 页 wireframe + Part3 视觉规范与动效。
> 配合 A1 数据字典 + A2 API 规范,前端可按 token 引用,实现"设计 → 代码"完整链路。
