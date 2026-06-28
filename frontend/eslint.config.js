// ESLint flat config (ESLint 9+)
// 覆盖: JS/TS/Vue + Vue 模板
// 参考: https://eslint.vuejs.org/user-guide/

import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import vue from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'

export default [
  // 全局 ignores
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'coverage/**',
      'playwright-report/**',
      'test-results/**',
      '**/*.d.ts',
      'auto-imports.d.ts',
      'components.d.ts',
      // Node.js CommonJS 配置文件(commitlint/postcss/tailwind 等)
      '*.config.cjs',
      '*.config.js',
      'postcss.config.*',
      'tailwind.config.*',
      'stylelint.config.*'
    ]
  },

  // 基础 JS 推荐规则
  js.configs.recommended,

  // TypeScript 推荐规则
  ...tseslint.configs.recommended,

  // Vue 3 推荐规则 (flat config)
  ...vue.configs['flat/recommended'],

  // TypeScript + Vue 解析器配置
  {
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tseslint.parser,
        extraFileExtensions: ['.vue'],
        ecmaVersion: 'latest',
        sourceType: 'module'
      }
    }
  },

  // 项目级规则调整
  {
    rules: {
      // Vue 3.4+ 可用 reactive props destructure,但项目未启用 experimental,关闭建议
      'vue/no-setup-props-destructure': 'off',
      // 允许 .vue 文件 <script setup> 顶层 await
      'vue/no-top-level-await': 'off',
      // 禁止 console.log/debug,允许 warn/error(用于真正的错误反馈)
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      // 允许 _ 前缀未使用参数 (catch err 等)
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }
      ],
      // 允许 any(迁移期妥协,见 docs/inspections/2026-06-28-lint-setup.md 的 baseline 84+42 现状)
      // 新代码 PR 应避免 any,但本规则不强制以免批量噪音
      '@typescript-eslint/no-explicit-any': 'off',
      // 空行规则宽松化
      'no-multiple-empty-lines': ['warn', { max: 2, maxEOF: 1, maxBOF: 0 }],
      // 字符串优先用单引号
      quotes: ['warn', 'single', { avoidEscape: true, allowTemplateLiterals: true }],
      // 分号警告
      semi: ['warn', 'never'],
      // 尾逗号 none
      'comma-dangle': 'off',
      // 非空断言(迁移期允许,见 baseline 注释)
      '@typescript-eslint/no-non-null-assertion': 'off',
      // 允许空 catch(拦截器已处理 / 业务上可忽略)
      'no-empty': ['error', { allowEmptyCatch: true }],
      // 强制 ES Module 类型导入写法(新规则,仅影响新代码习惯)
      '@typescript-eslint/consistent-type-imports': [
        'warn',
        { prefer: 'type-imports', fixStyle: 'separate-type-imports' }
      ],
      // 多单词组件名 — 例外列表(都是项目惯例命名,不会与 HTML 冲突)
      //   1. views/**: 路由页面组件,按业务名命名(Index / List / Detail 等)
      //   2. src/ui/Pager.vue: 设计系统中的 UI 组件,与 HTML <pager> 无冲突
      //   3. src/main.ts、App.vue: 根入口
      'vue/multi-word-component-names': [
        'error',
        {
          ignores: [
            'Index', 'List', 'Detail', 'Editor', 'Center', 'Overview', 'Shadow',
            'Group', 'History', 'Realtime', 'Environment', 'Gis', 'Pd', 'Prpd',
            'Temperature', 'Topology', 'Statistics', 'Log', 'Menu', 'Notify',
            'Organization', 'Role', 'Tenant', 'User', 'Dict', 'Alert',
            'Pager'
          ]
        }
      ]
    }
  },

  // 测试文件: 用 vitest globals
  {
    files: ['tests/**/*.{ts,tsx}', '**/*.test.{ts,tsx}', '**/*.spec.{ts,tsx}'],
    languageOptions: {
      globals: {
        describe: 'readonly',
        it: 'readonly',
        test: 'readonly',
        expect: 'readonly',
        beforeEach: 'readonly',
        afterEach: 'readonly',
        beforeAll: 'readonly',
        afterAll: 'readonly',
        vi: 'readonly'
      }
    }
  }
]
