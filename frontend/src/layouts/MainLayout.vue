<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { UserFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ============== 移动端 ==============
const isMobile = ref(window.innerWidth < 768)
const drawerVisible = ref(false)

function handleResize() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) drawerVisible.value = false
}
onMounted(() => window.addEventListener('resize', handleResize))
onBeforeUnmount(() => window.removeEventListener('resize', handleResize))

// ============== 菜单数据(支持二级) ==============
// 子项 hidden=true 表示不显示在菜单中(由父项或操作触发跳转,如物模型/工单详情)
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
      { path: '/device/overview', title: '设备总览', permission: 'device:overview' },
      { path: '/monitor/pd', title: '局放监测', permission: 'monitor:pd' },
      { path: '/monitor/prpd', title: 'PRPD 图谱', permission: 'monitor:prpd' },
      { path: '/monitor/temperature', title: '温度监测', permission: 'monitor:temperature' },
      { path: '/monitor/environment', title: '环境监测', permission: 'monitor:environment' },
      { path: '/monitor/gis', title: 'GIS 地图', permission: 'monitor:gis' },
      { path: '/monitor/topology', title: '电网拓扑', permission: 'monitor:topology' }
    ]
  },
  {
    title: '告警与运维', icon: 'Warning',
    children: [
      { path: '/alert/center', title: '告警中心', permission: 'alert:center' },
      { path: '/workorder/list', title: '工单管理', permission: 'workorder:list' },
      { path: '/knowledge/list', title: '知识库', permission: 'knowledge:list' },
      { path: '/ops/statistics', title: '运维统计', permission: 'ops:statistics' }
    ]
  },
  {
    title: '产品与设备', icon: 'Box',
    children: [
      { path: '/device/list', title: '设备列表', permission: 'device:list' },
      { path: '/device/group', title: '设备分组', permission: 'device:group' },
      { path: '/device/shadow', title: '设备影子', permission: 'device:shadow' },
      { path: '/product', title: '产品管理', permission: 'product:list' }
    ]
  },
  {
    title: '数据服务', icon: 'DataAnalysis',
    children: [
      { path: '/data/realtime', title: '实时数据', permission: 'data:realtime' },
      { path: '/data/history', title: '历史数据', permission: 'data:history' },
      { path: '/report/center', title: '报表中心', permission: 'report:center' }
    ]
  },
  {
    title: '大屏可视化', icon: 'PieChart',
    children: [
      { path: '/screen', title: '可视化大屏', permission: 'screen:view' },
      { path: '/iot-console', title: 'IoT 控制台', permission: 'iot-console:view' }
    ]
  },
  {
    title: '系统管理', icon: 'Setting',
    children: [
      { path: '/system/user', title: '用户管理', permission: 'system:user' },
      { path: '/system/role', title: '角色管理', permission: 'system:role' },
      { path: '/system/tenant', title: '租户管理', permission: 'system:tenant' },
      { path: '/system/organization', title: '组织架构', permission: 'system:organization' },
      { path: '/system/menu', title: '菜单管理', permission: 'system:menu' },
      { path: '/system/dict', title: '字典管理', permission: 'system:dict' },
      { path: '/system/log', title: '操作日志', permission: 'system:log' },
      { path: '/system/notify', title: '通知渠道', permission: 'system:notify' }
    ]
  }
]

// 过滤掉 hidden 子项,渲染菜单
function visibleItems(items?: MenuItem[]): MenuItem[] {
  if (!items) return []
  return items.filter(i => !i.hidden)
}

// 按 RBAC 过滤菜单
function hasPerm(p?: string): boolean {
  if (!p) return true
  if (userStore.roles.includes('SUPER_ADMIN')) return true
  return userStore.permissions.includes(p)
}

const visibleMenus = computed<MenuItem[]>(() =>
  menus
    .map(m => {
      if (!m.children) {
        // 一级菜单,按 permission 过滤
        return hasPerm(m.permission) ? m : null
      }
      // 二级菜单:有可见子项才显示父项
      const kids = visibleItems(m.children).filter(c => hasPerm(c.permission))
      return kids.length > 0 ? { ...m, children: kids } : null
    })
    .filter((m): m is MenuItem => m !== null)
)

// 当前激活项 = 当前路由路径
const activeMenu = computed(() => route.path)

// 打开的子菜单(用于刷新后保持展开状态)
const openedMenus = computed<string[]>(() => {
  const list: string[] = []
  for (const m of visibleMenus.value) {
    if (m.children?.some(c => c.path && route.path.startsWith(c.path))) {
      list.push(m.title)
    }
  }
  return list
})

// 面包屑:从路由路径反查父分组
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
  // 子菜单的 index 不是路径,忽略(虽然我们这里用 path 渲染 menu-item)
  if (!path || !path.startsWith('/')) return
  router.push(path)
  if (isMobile.value) drawerVisible.value = false
}
</script>

<template>
  <el-container class="layout">
    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobile" width="220px" class="layout-aside">
      <div class="layout-logo">
        <el-icon :size="22" class="layout-logo-icon"><Connection /></el-icon>
        <span class="layout-logo-text">IoT 平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="openedMenus"
        class="layout-menu"
        @select="onMenuSelect"
      >
        <!-- 一级菜单(无子项) -->
        <el-menu-item
          v-for="m in visibleMenus.filter(x => !x.children)"
          :key="m.path"
          :index="m.path!"
        >
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>

        <!-- 一级菜单(有子项) -->
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
            <span>{{ c.title }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
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
        <el-icon :size="22" class="layout-logo-icon"><Connection /></el-icon>
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
            <el-icon :size="22"><Expand /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="(b, i) in breadcrumb" :key="i">
              {{ b }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="layout-header-right">
          <el-dropdown trigger="click" @command="(c: string) => c === 'logout' && onLogout()">
            <span class="layout-user">
              <el-avatar :size="32" :icon="UserFilled" />
              <span class="layout-user-name">
                {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '未登录' }}
              </span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ userStore.userInfo?.tenantCode }}</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component, route }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="route.path" />
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
}

.layout-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-8;
  color: var(--iot-sidebar-text-active);
  background: var(--iot-sidebar-bg-hover);

  &-icon {
    color: var(--iot-sidebar-text-active);
  }

  &-mobile {
    background: var(--iot-sidebar-bg-hover);
  }

  &-text {
    font-size: $font-size-medium;
    font-weight: $font-weight-semibold;
    white-space: nowrap;
  }
}

.layout-menu {
  border-right: none;
  height: calc(100vh - 56px);
  background: transparent;
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

.layout-menu-btn {
  color: var(--iot-text-primary);
}

.layout-header-right {
  display: flex;
  align-items: center;
  gap: $spacing-12;
}

.layout-user {
  display: flex;
  align-items: center;
  gap: $spacing-4;
  cursor: pointer;
  padding: 0 $spacing-4;
  transition: color $transition-base;
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
</style>