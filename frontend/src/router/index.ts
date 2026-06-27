import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

NProgress.configure({ showSpinner: false })

const Layout = () => import('@/layouts/MainLayout.vue')

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Index.vue'),
    meta: { title: '登录', anonymous: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: '工作台', icon: 'odometer', permission: 'dashboard:view' }
      },
      {
        path: 'device/list',
        name: 'DeviceList',
        component: () => import('@/views/device/List.vue'),
        meta: { title: '设备列表', icon: 'monitor', permission: 'device:list' }
      },
      {
        path: 'device/group',
        name: 'DeviceGroup',
        component: () => import('@/views/device/Group.vue'),
        meta: { title: '设备分组', icon: 'folder', permission: 'device:group' }
      },
      {
        path: 'device/shadow',
        name: 'DeviceShadow',
        component: () => import('@/views/device/Shadow.vue'),
        meta: { title: '设备影子', icon: 'document', permission: 'device:shadow' }
      },
      {
        path: 'product',
        name: 'Product',
        component: () => import('@/views/product/Index.vue'),
        meta: { title: '产品管理', icon: 'box', permission: 'product:list' }
      },
      {
        path: 'product/thing-model/:id',
        name: 'ProductThingModel',
        component: () => import('@/views/product/ThingModel.vue'),
        meta: { title: '物模型编辑', activeMenu: '/product', permission: 'product:list' }
      },
      {
        path: 'data/realtime',
        name: 'DataRealtime',
        component: () => import('@/views/data/Realtime.vue'),
        meta: { title: '实时数据', icon: 'refresh', permission: 'data:realtime' }
      },
      {
        path: 'data/history',
        name: 'DataHistory',
        component: () => import('@/views/data/History.vue'),
        meta: { title: '历史数据', icon: 'histogram', permission: 'data:history' }
      },
      {
        path: 'rule/list',
        name: 'RuleList',
        component: () => import('@/views/rule/List.vue'),
        meta: { title: '规则列表', icon: 'set-up', permission: 'rule:list' }
      },
      {
        path: 'rule/alert',
        name: 'RuleAlert',
        component: () => import('@/views/rule/Alert.vue'),
        meta: { title: '告警记录', icon: 'warning', permission: 'rule:alert' }
      },
      {
        path: 'screen',
        name: 'Screen',
        component: () => import('@/views/screen/Index.vue'),
        meta: { title: '可视化大屏', icon: 'pie-chart', permission: 'screen:view' }
      },
      {
        path: 'system/user',
        name: 'SystemUser',
        component: () => import('@/views/system/User.vue'),
        meta: { title: '用户管理', icon: 'user', permission: 'system:user' }
      },
      {
        path: 'system/role',
        name: 'SystemRole',
        component: () => import('@/views/system/Role.vue'),
        meta: { title: '角色管理', icon: 'user-filled', permission: 'system:role' }
      },
      {
        path: 'system/menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/Menu.vue'),
        meta: { title: '菜单管理', icon: 'menu', permission: 'system:menu' }
      },
      {
        path: 'system/tenant',
        name: 'SystemTenant',
        component: () => import('@/views/system/Tenant.vue'),
        meta: { title: '租户管理', icon: 'office-building', permission: 'system:tenant' }
      },
      {
        path: 'system/notify',
        name: 'SystemNotify',
        component: () => import('@/views/system/Notify.vue'),
        meta: { title: '通知渠道', icon: 'message', permission: 'system:notify' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/login/Index.vue'),
    meta: { title: '页面不存在', anonymous: true }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  document.title = (to.meta?.title as string) || '物联网平台'

  if (to.meta?.anonymous) {
    return next()
  }

  const userStore = useUserStore()
  if (!userStore.isLoggedIn) {
    return next({ name: 'Login', query: { redirect: to.fullPath } })
  }

  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      userStore.reset()
      return next({ name: 'Login' })
    }
  }

  // 权限校验
  const perm = to.meta?.permission as string | undefined
  if (perm && !userStore.hasPermission(perm)) {
    ElMessage.error('无访问权限')
    return next(false)
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
