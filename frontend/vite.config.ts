/// <reference types="vitest" />
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'node:path'

// Vitest 模式下跳过 Element Plus 自动注册解析器:
// 解析器会在 SFC 模板里注入 `import { ElXxx } from 'element-plus'`,
// 真实加载会触发 .css 资源请求,happy-dom + Node 环境下 ERR_UNKNOWN_FILE_EXTENSION
// 测试中所有 el-* 组件已通过 tests/setup.ts 的 config.global.stubs 替换为 stub
const isVitest = process.env.VITEST === 'true' || process.env.VITEST === '1'
const uiResolvers = isVitest ? [] : [ElementPlusResolver()]

export default defineConfig(({ mode }) => {
  // mode 由 CLI 决定:
  //   npm run dev  -> 'development' -> 加载 .env.development
  //   npm run build -> 'production' -> 加载 .env.production
  const env = loadEnv(mode, process.cwd(), '')
  const apiBase = env.VITE_API_BASE_URL || '/api'
  // dev 下 /api 是相对路径,需要 proxy 转发到真实后端
  const proxyTarget = apiBase.startsWith('http')
    ? apiBase.replace(/\/api$/, '')
    : `http://localhost:${process.env.BACKEND_PORT || 33412}`

  return {
    plugins: [
      vue(),
      AutoImport({ resolvers: uiResolvers }),
      Components({ resolvers: uiResolvers })
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      host: '0.0.0.0',
      // 端口规则: 33400/33401 被 pi-node 占用,跳过;Frontend dev 用 33411
      port: 33411,
      strictPort: false,
      // 允许通过这些域名访问 dev server(Vite 5+ 默认有 host 检查)
      allowedHosts: ['huntercat.cn', '.huntercat.cn'],
      proxy: {
        // iot-console 端点只在 broker 上(role=iot),转发到 broker 端口
        // 注意:必须放在 /api 之前,vite proxy 按声明顺序匹配
        '/api/iot-console': {
          target: `http://localhost:${process.env.BROKER_PORT || '9101'}`,
          changeOrigin: true
        },
        '/api': {
          target: proxyTarget,
          changeOrigin: true
        },
        // 后端 context-path 是 /api,WebSocket 真实路径是 /api/ws/shadow
        // 注意:必须带 ws: true 才会升级为 WebSocket 连接(默认只代理 HTTP)
        '/api/ws': {
          target: proxyTarget,
          ws: true,
          changeOrigin: true
        },
        // 兼容历史代码:仍保留 /ws 前缀的 WS 代理
        '/ws': {
          target: proxyTarget,
          ws: true,
          changeOrigin: true
        }
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          // 使用 modern API 解决 @use 在某些组件中的解析问题
          api: 'modern-compiler'
        }
      }
    },
    build: {
      target: 'es2015',
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          manualChunks: {
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
            'echarts': ['echarts', 'vue-echarts']
          }
        }
      }
    },
    // ============== 测试配置 ==============
    test: {
      // 全局 API: describe / it / expect / vi / beforeEach 等
      globals: true,
      // 浏览器环境模拟
      environment: 'happy-dom',
      // 测试入口: 单元测试 + 组件测试
      include: [
        'src/**/*.{test,spec}.{ts,tsx}',
        'tests/unit/**/*.{test,spec}.{ts,tsx}'
      ],
      // 全局 setup: 注册 mock、扩展 expect 等
      setupFiles: ['./tests/setup.ts'],
      // CSS / 静态资源在测试中通常不需要
      css: false,
      // 测试运行优化
      isolate: true,
      // 覆盖率
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'json-summary', 'lcov'],
        reportsDirectory: './coverage',
        // 计入阈值的核心模块(不含纯渲染页面 / 业务 API endpoint wrapper)
        include: [
          'src/stores/**',
          'src/utils/**',
          'src/components/**',
          'src/ui/**',           // 设计系统组件
          'src/composables/**',  // 组合式函数(useTable / useDict)
          'src/api/crud.ts'      // CrudApi 适配器(其他业务 endpoint wrapper 由 E2E 覆盖)
        ],
        exclude: [
          'src/**/*.{test,spec}.{ts,tsx}',
          'src/main.ts',
          'src/**/index.ts',
          'src/**/types.ts',
          'src/**/*.d.ts',
          'src/**/*.config.ts',
          'src/**/mocks/**',
          // 业务 API endpoint wrapper(pageXxx / createXxx 等)是简单的 axios 调用,
          // 由 E2E 间接覆盖,不计入单元覆盖率门槛
          'src/api/alert/**',
          'src/api/device/**',
          'src/api/iot/**',
          'src/api/knowledge/**',
          'src/api/login/**',
          'src/api/ops/**',
          'src/api/product/**',
          'src/api/report/**',
          'src/api/rule/**',
          'src/api/screen/**',
          'src/api/system/**',
          'src/api/workorder/**',
          'src/api/dashboard.ts',
          'src/api/monitor.ts',
          'src/api/topology.ts',
          // 业务页面由 E2E 覆盖
          'src/views/**'
        ],
        // 阈值: 渐进式提升
        //   当前均值 ~70%(不含业务 endpoint wrapper),作为中期目标
        //   设计系统本身(ui/) 实际在 90%+
        thresholds: {
          lines: 65,
          functions: 65,
          branches: 55,
          statements: 65
        }
      }
    }
  }
})