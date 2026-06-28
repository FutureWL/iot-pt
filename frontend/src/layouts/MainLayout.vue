<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import {
  UserFilled, Fold, Expand, Sunny, MoonNight, Monitor, Check
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()

// ============== 移动端 ==============
const isMobile = ref(window.innerWidth < 768)
const drawerVisible = ref(false)

function handleResize() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) drawerVisible.value = false
}
onMounted(() => window.addEventListener('resize', handleResize))
onBeforeUnmount(() => window.removeEventListener('resize', handleResize))

// ============== 侧边栏折叠(持久化) ==============
const collapsed = ref(localStorage.getItem('iot-sidebar-collapsed') === 'true')

watch(collapsed, (v) => {
  localStorage.setItem('iot-sidebar-collapsed', String(v))
})

const asideWidth = computed(() => collapsed.value ? '64px' : '220px')

// ============== 主题切换 ==============
// 委托给 Pinia store,MainLayout 不再持有主题状态
function onThemeSelect(cmd: 'light' | 'dark' | 'system') {
  themeStore.setMode(cmd)
}

// ============== 菜单数据 ==============
interface MenuItem {
  path?: string
  title: string
  icon?: string
  permission?: string
  hidden?: boolean
  children?: MenuItem[]
}

const menus: MenuItem[] = [
  { path: '/dashboard', title: '工作台', icon: 'Odometer' },
  {
    title: '监测中心', icon: 'Monitor',
    children: [
      { path: '/device/overview',  title: '设备总览', icon: 'Odometer',     permission: 'device:overview' },
      { path: '/monitor/pd',       title: '局放监测', icon: 'Cpu',          permission: 'monitor:pd' },
      { path: '/monitor/prpd',     title: 'PRPD 图谱', icon: 'DataLine',    permission: 'monitor:prpd' },
      { path: '/monitor/temperature', title: '温度监测', icon: 'Histogram',   permission: 'monitor:temperature' },
      { path: '/monitor/environment', title: '环境监测', icon: 'Cloudy',      permission: 'monitor:environment' },
      { path: '/monitor/gis',      title: 'GIS 地图', icon: 'Location',     permission: 'monitor:gis' },
      { path: '/monitor/topology', title: '电网拓扑', icon: 'Share',        permission: 'monitor:topology' }
    ]
  },
  {
    title: '告警与运维', icon: 'Warning',
    children: [
      { path: '/alert/center',     title: '告警中心', icon: 'Warning',       permission: 'alert:center' },
      { path: '/workorder/list',   title: '工单管理', icon: 'Tickets',       permission: 'workorder:list' },
      { path: '/knowledge/list',   title: '知识库',   icon: 'Reading',       permission: 'knowledge:list' },
      { path: '/ops/statistics',   title: '运维统计', icon: 'DataAnalysis',  permission: 'ops:statistics' }
    ]
  },
  {
    title: '产品与设备', icon: 'Box',
    children: [
      { path: '/device/list',      title: '设备列表', icon: 'List',       permission: 'device:list' },
      { path: '/device/group',     title: '设备分组', icon: 'Folder',     permission: 'device:group' },
      { path: '/device/list',      title: '设备影子', icon: 'Document',   permission: 'device:shadow' },
      { path: '/product',          title: '产品管理', icon: 'Goods',      permission: 'product:list' }
    ]
  },
  {
    title: '数据服务', icon: 'DataAnalysis',
    children: [
      { path: '/data/realtime',    title: '实时数据', icon: 'VideoPlay',     permission: 'data:realtime' },
      { path: '/data/history',     title: '历史数据', icon: 'Clock',         permission: 'data:history' },
      { path: '/report/center',    title: '报表中心', icon: 'DocumentCopy',  permission: 'report:center' }
    ]
  },
  {
    title: '大屏可视化', icon: 'PieChart',
    children: [
      { path: '/screen',           title: '可视化大屏', icon: 'Monitor',    permission: 'screen:view' },
      { path: '/iot-console',      title: 'IoT 控制台', icon: 'Cpu',        permission: 'iot-console:view' }
    ]
  },
  {
    title: '系统管理', icon: 'Setting',
    children: [
      { path: '/system/user',         title: '用户管理',   icon: 'User',            permission: 'system:user' },
      { path: '/system/role',         title: '角色管理',   icon: 'UserFilled',      permission: 'system:role' },
      { path: '/system/tenant',       title: '租户管理',   icon: 'OfficeBuilding',  permission: 'system:tenant' },
      { path: '/system/organization', title: '组织架构',   icon: 'Connection',      permission: 'system:organization' },
      { path: '/system/menu',         title: '菜单管理',   icon: 'Menu',            permission: 'system:menu' },
      { path: '/system/dict',         title: '字典管理',   icon: 'Collection',      permission: 'system:dict' },
      { path: '/system/log',          title: '操作日志',   icon: 'DocumentChecked', permission: 'system:log' },
      { path: '/system/notify',       title: '通知渠道',   icon: 'Message',         permission: 'system:notify' }
    ]
  }
]

function visibleItems(items?: MenuItem[]): MenuItem[] {
  if (!items) return []
  return items.filter(i => !i.hidden)
}

function hasPerm(p?: string): boolean {
  if (!p) return true
  if (userStore.roles.includes('SUPER_ADMIN')) return true
  return userStore.permissions.includes(p)
}

const visibleMenus = computed<MenuItem[]>(() =>
  menus
    .map(m => {
      if (!m.children) {
        return hasPerm(m.permission) ? m : null
      }
      const kids = visibleItems(m.children).filter(c => hasPerm(c.permission))
      return kids.length > 0 ? { ...m, children: kids } : null
    })
    .filter((m): m is MenuItem => m !== null)
)

const activeMenu = computed(() => route.path)

const openedMenus = computed<string[]>(() => {
  const list: string[] = []
  for (const m of visibleMenus.value) {
    if (m.children?.some(c => c.path && route.path.startsWith(c.path))) {
      list.push(m.title)
    }
  }
  return list
})

const breadcrumb = computed<string[]>(() => {
  const title = (route.meta?.title as string) || ''
  const items = ['物联网平台']
  if (!title || title === '工作台') {
    items.push('工作台')
    return items
  }
  for (const m of visibleMenus.value) {
    if (m.children?.some(c => c.path === route.path || (c.path && route.path.startsWith(c.path + '/')))) {
      items.push(m.title)
      items.push(title)
      return items
    }
  }
  items.push(title)
  return items
})

// ============== 操作 ==============
async function onLogout() {
  await userStore.logout()
  router.push('/login')
}

function onMenuSelect(path: string) {
  if (!path || !path.startsWith('/')) return
  router.push(path)
  if (isMobile.value) drawerVisible.value = false
}

function toggleCollapsed() {
  collapsed.value = !collapsed.value
}

// ============== 生命周期 ==============
onMounted(() => {
  // 初始化主题 + 注册系统主题变化监听(store 内部 refCount 防重复)
  themeStore.init()
})

onBeforeUnmount(() => {
  // 释放系统主题监听
  themeStore.dispose()
})
</script>

<template>
  <el-container class="layout">
    <!-- 桌面端侧边栏 -->
    <el-aside
      v-if="!isMobile"
      :width="asideWidth"
      class="layout-aside"
    >
      <div class="layout-logo">
        <el-icon
          :size="22"
          class="layout-logo-icon"
        >
          <Connection />
        </el-icon>
        <span
          v-show="!collapsed"
          class="layout-logo-text"
        >IoT 平台</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :default-openeds="openedMenus"
        :collapse="collapsed"
        :collapse-transition="false"
        class="layout-menu"
        @select="onMenuSelect"
      >
        <el-menu-item
          v-for="m in visibleMenus.filter(x => !x.children)"
          :key="m.path"
          :index="m.path!"
        >
          <el-icon><component :is="m.icon" /></el-icon>
          <template #title>
            {{ m.title }}
          </template>
        </el-menu-item>

        <el-sub-menu
          v-for="m in visibleMenus.filter(x => x.children)"
          :key="m.title"
          :index="m.title"
        >
          <template #title>
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.title }}</span>
          </template>
          <el-menu-item
            v-for="c in m.children"
            :key="c.path"
            :index="c.path!"
          >
            <el-icon><component :is="c.icon || 'Minus'" /></el-icon>
            <template #title>
              {{ c.title }}
            </template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>

      <!-- 折叠切换按钮(底部) -->
      <div
        class="layout-collapse-btn"
        @click="toggleCollapsed"
      >
        <el-icon :size="18">
          <component :is="collapsed ? 'Expand' : 'Fold'" />
        </el-icon>
        <span v-show="!collapsed">收起菜单</span>
      </div>
    </el-aside>

    <!-- 移动端抽屉菜单 -->
    <el-drawer
      v-if="isMobile"
      v-model="drawerVisible"
      direction="ltr"
      size="78%"
      :with-header="false"
    >
      <div class="layout-logo layout-logo-mobile">
        <el-icon
          :size="22"
          class="layout-logo-icon"
        >
          <Connection />
        </el-icon>
        <span class="layout-logo-text">IoT 平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="openedMenus"
        class="layout-menu"
        @select="onMenuSelect"
      >
        <el-menu-item
          v-for="m in visibleMenus.filter(x => !x.children)"
          :key="m.path"
          :index="m.path!"
        >
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
        <el-sub-menu
          v-for="m in visibleMenus.filter(x => x.children)"
          :key="m.title"
          :index="m.title"
        >
          <template #title>
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.title }}</span>
          </template>
          <el-menu-item
            v-for="c in m.children"
            :key="c.path"
            :index="c.path!"
          >
            <span>{{ c.title }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-drawer>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="layout-header">
        <div class="layout-header-left">
          <el-button
            v-if="isMobile"
            link
            class="layout-menu-btn"
            @click="drawerVisible = true"
          >
            <el-icon :size="22">
              <Expand />
            </el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="(b, i) in breadcrumb"
              :key="i"
            >
              {{ b }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="layout-header-right">
          <!-- 主题切换(用原生 title 避免 el-tooltip 拦截 el-dropdown 触发) -->
          <el-dropdown
            trigger="click"
            @command="onThemeSelect"
          >
            <el-button
              link
              class="header-icon-btn"
              :title="`当前主题:${themeStore.mode === 'system' ? '跟随系统' : themeStore.mode === 'dark' ? '暗色' : '亮色'} · 点击切换`"
            >
              <el-icon :size="18">
                <component :is="themeStore.mode === 'light' ? 'Sunny' : themeStore.mode === 'dark' ? 'MoonNight' : 'Monitor'" />
              </el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="light">
                  <el-icon><Sunny /></el-icon>
                  <span class="theme-label">浅色</span>
                  <el-icon
                    v-if="themeStore.mode === 'light'"
                    class="theme-check"
                  >
                    <Check />
                  </el-icon>
                </el-dropdown-item>
                <el-dropdown-item command="dark">
                  <el-icon><MoonNight /></el-icon>
                  <span class="theme-label">暗色</span>
                  <el-icon
                    v-if="themeStore.mode === 'dark'"
                    class="theme-check"
                  >
                    <Check />
                  </el-icon>
                </el-dropdown-item>
                <el-dropdown-item command="system">
                  <el-icon><Monitor /></el-icon>
                  <span class="theme-label">跟随系统</span>
                  <el-icon
                    v-if="themeStore.mode === 'system'"
                    class="theme-check"
                  >
                    <Check />
                  </el-icon>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- 用户菜单 -->
          <el-dropdown
            trigger="click"
            @command="(c: string) => c === 'logout' && onLogout()"
          >
            <span class="layout-user">
              <el-avatar
                :size="32"
                :icon="UserFilled"
              />
              <span class="layout-user-name">
                {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '未登录' }}
              </span>
              <el-icon><Fold /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ userStore.userInfo?.tenantCode }}
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component, route }">
          <transition
            name="fade"
            mode="out-in"
          >
            <component
              :is="Component"
              :key="route.path"
            />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.layout {
  height: 100vh;
  width: 100%;
}

.layout-aside {
  background: var(--iot-sidebar-bg);
  overflow-x: hidden;
  overflow-y: auto;
  transition: width $transition-base;
  display: flex;
  flex-direction: column;
}

.layout-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-8;
  color: var(--iot-sidebar-text-active);
  background: var(--iot-sidebar-bg-hover);
  flex-shrink: 0;

  &-icon { color: var(--iot-sidebar-text-active); }
  &-mobile { background: var(--iot-sidebar-bg-hover); }
  &-text {
    font-size: $font-size-medium;
    font-weight: $font-weight-semibold;
    white-space: nowrap;
    overflow: hidden;
  }
}

.layout-menu {
  border-right: none;
  flex: 1;
  background: transparent;
  overflow-y: auto;
  overflow-x: hidden;
}

// 折叠切换按钮
.layout-collapse-btn {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-8;
  color: var(--iot-sidebar-text);
  cursor: pointer;
  border-top: 1px solid var(--iot-sidebar-border);
  transition: color $transition-fast, background $transition-fast;
  flex-shrink: 0;
  user-select: none;
  &:hover {
    color: var(--iot-sidebar-text-active);
    background: var(--iot-sidebar-bg-hover);
  }
  span {
    font-size: $font-size-small;
    white-space: nowrap;
  }
}

// 折叠时 el-menu 子菜单弹层位置微调
:deep(.el-menu--collapse) {
  width: 64px;
}

.layout-header {
  background: var(--iot-header-bg);
  border-bottom: 1px solid var(--iot-header-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 $spacing-16;
  height: 56px;
}

.layout-header-left {
  display: flex;
  align-items: center;
  gap: $spacing-12;
}

.layout-menu-btn { color: var(--iot-text-primary); }

.layout-header-right {
  display: flex;
  align-items: center;
  gap: $spacing-12;
}

.header-icon-btn {
  color: var(--iot-text-regular);
  &:hover { color: var(--iot-color-primary); }
}

.theme-label { margin: 0 $spacing-8; }
.theme-check { color: var(--iot-color-primary); margin-left: auto; }

.layout-user {
  display: flex;
  align-items: center;
  gap: $spacing-4;
  cursor: pointer;
  padding: 0 $spacing-4;
  transition: color $transition-base;
  color: var(--iot-text-primary);
  &:hover { color: var(--iot-color-primary); }
}

.layout-user-name {
  font-size: $font-size-base;
  @media (max-width: $breakpoint-xs) { display: none; }
}

.layout-main {
  background: var(--iot-bg-page);
  padding: 0;
  overflow: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity $transition-fast;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

:deep(.el-drawer__body) {
  padding: 0;
  background: var(--iot-sidebar-bg);
}

// 主题切换 dropdown item 内的 icon 对齐
:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  min-width: 140px;
}
</style>