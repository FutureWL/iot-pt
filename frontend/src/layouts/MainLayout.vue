<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

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
        <el-icon :size="22" color="#fff"><Connection /></el-icon>
        <span class="layout-logo-text">IoT 平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="layout-menu"
        background-color="#001529"
        text-color="#c0c4cc"
        active-text-color="#fff"
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
        <el-icon :size="22" color="#fff"><Connection /></el-icon>
        <span class="layout-logo-text">IoT 平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="layout-menu"
        background-color="#001529"
        text-color="#c0c4cc"
        active-text-color="#fff"
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
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.layout {
  height: 100vh;
  width: 100%;
}

.layout-aside {
  background: #001529;
  overflow-x: hidden;
  overflow-y: auto;
  transition: width 0.2s;
}

.layout-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  background: #002140;
  &-mobile { background: #002140; }
  &-text {
    font-size: 16px;
    font-weight: 600;
    white-space: nowrap;
  }
}

.layout-menu {
  border-right: none;
  height: calc(100vh - 56px);
}

.layout-header {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 56px;
}

.layout-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.layout-menu-btn {
  color: #303133;
}

.layout-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.layout-user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 0 6px;
  &:hover { color: #409eff; }
}

.layout-user-name {
  font-size: 14px;
  @media (max-width: 480px) { display: none; }
}

.layout-main {
  background: #f5f7fa;
  padding: 0;
  overflow: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

:deep(.el-drawer__body) {
  padding: 0;
  background: #001529;
}
</style>
