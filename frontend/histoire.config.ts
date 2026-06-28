import { defineConfig } from 'histoire'
import { HstVue } from '@histoire/plugin-vue'

/**
 * Histoire — 设计系统组件故事/文档
 *
 * 用法:
 *   npm run story:dev      启动 http://localhost:6006
 *   npm run story:build    生成静态站点
 *
 * 故事文件位置:
 *   src/ui/*.stories.ts    每个组件一个 .stories.ts
 *
 * 目标:
 *   - 让设计师/产品/新人 不需要跑 dev server 就能看到所有组件
 *   - 提供可交互的 props playground
 *   - 替代手工维护的 Storybook / 静态文档页
 */
export default defineConfig({
  plugins: [HstVue()],
  // 仅扫描 ui 目录的故事文件,避免污染
  storyMatch: [
    'src/ui/*.story.vue'
  ],
  // 故事文件关联 vue 渲染插件(默认 *.vue 已注册,*.story.vue 也走同一插件)
  supportMatch: [
    {
      id: 'vue-stories',
      patterns: ['**/*.story.vue'],
      pluginIds: ['vue3']
    }
  ],
  // 主题
  title: 'IoT Platform — 设计系统',
  theme: {
    title: 'IoT Platform 设计系统'
  },
  // 故事分组定义
  tree: {
    groups: [
      { id: 'basic', title: '基础原子' },
      { id: 'crud', title: 'CRUD 三件套' }
    ]
  },
  // Vite/Histoire 配置
  vite: {
    // Element Plus 在 SSR 收集阶段会触发 CSS 解析报错;
    // 标记为 SSR 内联依赖 + noExternal 跳过 css 解析,让故事文件能正常加载组件
    ssr: {
      noExternal: ['element-plus', '@element-plus/icons-vue']
    },
    optimizeDeps: {
      exclude: ['@amap/amap-jsapi-loader']
    }
  },
  // Histoire 的 Node 端收集阶段需要把依赖打进 bundle
  viteNodeInlineDeps: [
    'element-plus',
    '@element-plus/icons-vue'
  ]
})