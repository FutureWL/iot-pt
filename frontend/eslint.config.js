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
      'components.d.ts'
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
      // 允许 console.warn/error (项目内多处使用)
      'no-console': 'off',
      // 允许 _ 前缀未使用参数 (catch err 等)
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }
      ],
      // 允许 any (迁移期妥协,后续严格化)
      '@typescript-eslint/no-explicit-any': 'off',
      // 空行规则宽松化
      'no-multiple-empty-lines': ['warn', { max: 2, maxEOF: 1, maxBOF: 0 }],
      // 字符串优先用单引号
      'quotes': ['warn', 'single', { avoidEscape: true, allowTemplateLiterals: true }],
      // 分号警告
      'semi': ['warn', 'never'],
      // 尾逗号 none
      'comma-dangle': 'off',
      // TS 严格模式对项目友好,但允许 escape hatch
      '@typescript-eslint/no-non-null-assertion': 'off'
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