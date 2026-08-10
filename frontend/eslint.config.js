import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'
import prettier from 'eslint-config-prettier'
import globals from 'globals'

/**
 * ESLint flat config(ESLint 9+)。
 * 目标:
 *   1. 守住 TS 类型问题(不能上生产)
 *   2. 拦住明显错误(unused / no-debugger / no-undef)
 *   3. 不跟 Prettier 冲突(关闭格式类规则)
 *   4. 不强制风格化(避免跟团队习惯打架)
 */
export default [
  // 全局忽略
  {
    ignores: [
      'node_modules/**',
      'dist/**',
      'dist-ssr/**',
      'auto-imports.d.ts',
      'components.d.ts',
      'public/**',
      'cypress/e2e/**',
    ],
  },

  // 基础 JS 推荐
  js.configs.recommended,

  // TypeScript
  ...tseslint.configs.recommended,

  // Vue 3
  ...pluginVue.configs['flat/recommended'],

  // 自定义规则
  {
    files: ['**/*.{js,ts,vue}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      parser: vueParser,
      parserOptions: {
        parser: tseslint.parser,
        ecmaVersion: 2022,
        sourceType: 'module',
        extraFileExtensions: ['.vue'],
      },
      globals: {
        ...globals.browser,
        ...globals.node,
        // Vite / Vue 自动 import
        defineProps: 'readonly',
        defineEmits: 'readonly',
        defineExpose: 'readonly',
        withDefaults: 'readonly',
        ref: 'readonly',
        reactive: 'readonly',
        computed: 'readonly',
        watch: 'readonly',
        watchEffect: 'readonly',
        inject: 'readonly',
        provide: 'readonly',
        onMounted: 'readonly',
        onUnmounted: 'readonly',
        onBeforeMount: 'readonly',
        onBeforeUnmount: 'readonly',
      },
    },
    rules: {
      // TS 推荐规则过严,放宽
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      '@typescript-eslint/no-non-null-assertion': 'off',

      // Vue 推荐规则过严,放宽
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'warn',
      'vue/no-mutating-props': 'off', // 表单 v-model 直接绑 prop 是项目既定模式(父子双向绑定),逐处 disable 不可靠,全局关
      'vue/html-self-closing': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-indent': 'off',
      'vue/attributes-order': 'off',
      'vue/first-attribute-linebreak': 'off',
      'vue/html-closing-bracket-newline': 'off',

      // 基础规则:error / warn 二选一
      'no-console': ['warn', { allow: ['warn', 'error', 'info'] }],
      'no-debugger': 'error',
      'no-undef': 'off', // TS 已覆盖
      'no-unused-vars': 'off', // TS 已覆盖
      'prefer-const': 'warn',
      eqeqeq: ['error', 'smart'],
    },
  },

  // 测试文件放宽
  {
    files: ['**/*.test.ts', '**/*.spec.ts', 'cypress/**/*.js'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      'no-console': 'off',
    },
  },

  // 跟 Prettier 不冲突(必须放最后)
  prettier,
]
