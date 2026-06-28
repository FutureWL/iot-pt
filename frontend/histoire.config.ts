import { defineConfig } from 'histoire'
import { HstVue } from '@histoire/plugin-vue'

/**
 * Histoire — 设计系统组件故事/文档
 *
 * 用法:
 *   npm run story:dev      启动 http://localhost:6006
 *   npm run story:build    生成静态站点到 dist-story/
 *   npm run story:preview  预览构建产物
 *
 * 故事文件位置:
 *   src/ui/*.story.vue    每个组件一个 .story.vue
 *
 * 已知问题与 workaround:
 *   - Histoire v1 beta 在 build 阶段会把 vendor 模块转成 file:// URL,
 *     导致 Rollup 无法解析。已通过 postinstall 脚本 patch-histoire.mjs
 *     修复 vendors.js(把 pathToFileURL 改成直接返回原路径)。
 *   - vite.alias 把 @histoire/vendors/vue 映射到普通模块路径,
 *     配合 patch 让 build 完整可用。
 */
export default defineConfig({
  plugins: [HstVue()],
  // 构建输出到 dist-story/(与主项目 dist/ 区分)
  outDir: 'dist-story',
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
    // 把 @histoire/vendors/vue 映射到普通模块路径,
    // 配合 patch-histoire 修复 build 阶段 bundling 问题
    resolve: {
      alias: [
        { find: '@histoire/vendors/vue', replacement: '@histoire/vendors/dist/client/b-vue.js' }
      ]
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