// Cypress 标准 e2e — 用于本地 GUI 跑
// 用法: npx cypress run (需先装 cypress: pnpm add -D cypress)
// 注意:CI 默认跑 scripts/e2e/*.test.mjs(零依赖),cypress 只是开发者可选 GUI

describe('知驭 ZhiYu - 3 页面可访问性', () => {
  before(() => {
    // 登出(如有)
    cy.clearLocalStorage()
  })

  it('登录页能打开', () => {
    cy.visit('/login')
    cy.contains('演示账号')
  })

  it('登录后能跳到 Dashboard', () => {
    cy.visit('/login')
    cy.get('input[placeholder*="用户名"]').type('admin')
    cy.get('input[type="password"]').type('pmo123')
    cy.contains('登录').click()
    cy.url().should('eq', Cypress.config('baseUrl') + '/')
    cy.contains('执行中项目')
    cy.contains('活跃项目')
  })

  it('Dashboard 4 个 KPI 卡都显示数字', () => {
    cy.visit('/')
    cy.get('.kpi-card__value').should('have.length', 4)
    // 每个值都是数字(不是 "-")
    cy.get('.kpi-card__value').each($el => {
      const t = $el.text().trim()
      expect(t).to.match(/^\d+$/)
    })
  })

  it('Dashboard 2 个 ECharts 饼图渲染 canvas', () => {
    cy.visit('/')
    cy.get('[_echarts_instance_]').should('have.length.at.least', 2)
  })

  it('项目页能列出种子项目', () => {
    cy.visit('/projects')
    cy.contains('项目列表')
    cy.get('table tbody tr').its('length').should('be.gt', 0)
  })

  it('立项审批页能列出种子立项', () => {
    cy.visit('/initiations')
    cy.contains('立项列表')
    cy.contains('AI智能客服系统')
  })
})
