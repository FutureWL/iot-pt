import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'node:path'

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
      AutoImport({ resolvers: [ElementPlusResolver()] }),
      Components({ resolvers: [ElementPlusResolver()] })
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
        '/api': {
          target: proxyTarget,
          changeOrigin: true
        },
        '/ws': {
          target: proxyTarget,
          ws: true,
          changeOrigin: true
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
    }
  }
})