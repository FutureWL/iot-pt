import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
// @ts-ignore
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
// 加载 Element Plus 基础样式(ElMessage / ElNotification / ElMessageBox / ElLoading
// 等以命令式 API 调用的组件不在模板中,unplugin-vue-components 不会为它们注入 CSS,
// 这里显式补齐,否则提示/通知会因缺 position:fixed 等样式而看不见)
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/index.scss'
import { useThemeStore } from './stores/theme'

const app = createApp(App)

app.use(createPinia())

// 主题初始化(必须在 mount 之前 — 读 localStorage + 写 .dark class + 注册 matchMedia 监听)
// init() 内部使用 refCount 防止多实例重复注册监听器
const themeStore = useThemeStore()
themeStore.init()

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })

app.mount('#app')
