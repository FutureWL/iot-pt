<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { UserFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 移动端状态
const isMobile = ref(window.innerWidth < 768)
const drawerVisible = ref(false)

function handleResize() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) drawerVisible.value = false
}
onMounted(() => window.addEventListener('resize', handleResize))
onBeforeUnmount(() => window.removeEventListener('resize', handleResize))

// 菜单数据
const menus = [
  { path: '/dashboard',    title: '工作台',     icon: 'Odometer' },
  { path: '/device/list',  title: '设备列表',   icon: 'Monitor' },
  { path: '/device/group', title: '设备分组',   icon: 'Folder' },
  { path: '/product',      title: '产品管理',   icon: 'Box' },
  { path: '/data/realtime',title: '实时数据',   icon: 'Refresh' },
  { path: '/data/history', title: '历史数据',   icon: 'Histogram' },
  { path: '/rule/list',    title: '规则引擎',   icon: 'SetUp' },
  { path: '/rule/alert',   title: '告警记录',   icon: 'Warning' },
  { path: '/screen',       title: '可视化大屏', icon: 'PieChart' },
  { path: '/system/user',  title: '用户管理',   icon: 'User' },
  { path: '/system/role',  title: '角色管理',   icon: 'UserFilled' },
  { path: '/system/tenant',title: '租户管理',   icon: 'OfficeBuilding' },
  { path: '/system/notify',title: '通知渠道',   icon: 'Message' }
]

const activeMenu = computed(() => route.path)

const breadcrumb = computed(() => {
  const title = route.meta?.title as string
  return title ? ['物联网平台', title] : ['物联网平台']
})

async function onLogout() {
  await userStore.logout()
  router.push('/login')
}

function onMenuSelect(path: string) {
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
        class="layout-menu"
        @select="onMenuSelect"
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
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
        class="layout-menu"
        @select="onMenuSelect"
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
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
