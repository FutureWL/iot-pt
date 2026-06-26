import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'node:path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 端口规则: 33400/33401 被 pi-node 占用,跳过;Backend API 用 33412
  const backendBase = (env.VITE_API_BASE_URL || 'http://localhost:33412/api').replace(/\/api$/, '')

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
      // 端口规则: 33400 被 pi-node 占用,跳过;Frontend dev 用 33411
      port: 33411,
      strictPort: false,
      proxy: {
        '/api': {
          target: backendBase,
          changeOrigin: true
        },
        '/ws': {
          target: backendBase,
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
