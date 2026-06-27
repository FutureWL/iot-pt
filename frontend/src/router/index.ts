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
      // ============ 工作台 ============
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: '工作台', icon: 'odometer', permission: 'dashboard:view' }
      },

      // ============ 监测中心 ============
      {
        path: 'device/overview',
        name: 'DeviceOverview',
        component: () => import('@/views/device/Overview.vue'),
        meta: { title: '设备总览', icon: 'monitor', permission: 'device:overview' }
      },
      {
        path: 'monitor/pd',
        name: 'MonitorPd',
        component: () => import('@/views/monitor/Pd.vue'),
        meta: { title: '局放监测', icon: 'cpu', permission: 'monitor:pd' }
      },
      {
        path: 'monitor/prpd',
        name: 'MonitorPrpd',
        component: () => import('@/views/monitor/Prpd.vue'),
        meta: { title: 'PRPD 图谱', icon: 'data-line', permission: 'monitor:prpd' }
      },
      {
        path: 'monitor/temperature',
        name: 'MonitorTemperature',
        component: () => import('@/views/monitor/Temperature.vue'),
        meta: { title: '温度监测', icon: 'thermometer', permission: 'monitor:temperature' }
      },
      {
        path: 'monitor/environment',
        name: 'MonitorEnvironment',
        component: () => import('@/views/monitor/Environment.vue'),
        meta: { title: '环境监测', icon: 'cloudy', permission: 'monitor:environment' }
      },
      {
        path: 'monitor/gis',
        name: 'MonitorGis',
        component: () => import('@/views/monitor/Gis.vue'),
        meta: { title: 'GIS 地图', icon: 'location', permission: 'monitor:gis' }
      },

      // ============ 告警与运维 ============
      {
        path: 'alert/center',
        name: 'AlertCenter',
        component: () => import('@/views/alert/Center.vue'),
        meta: { title: '告警中心', icon: 'warning', permission: 'alert:center' }
      },
      {
        path: 'workorder/list',
        name: 'WorkOrderList',
        component: () => import('@/views/workorder/List.vue'),
        meta: { title: '工单管理', icon: 'tickets', permission: 'workorder:list' }
      },
      {
        path: 'workorder/detail/:id',
        name: 'WorkOrderDetail',
        component: () => import('@/views/workorder/Detail.vue'),
        meta: { title: '工单详情', activeMenu: '/workorder/list', permission: 'workorder:list' }
      },
      {
        path: 'knowledge/list',
        name: 'KnowledgeList',
        component: () => import('@/views/knowledge/List.vue'),
        meta: { title: '知识库', icon: 'reading', permission: 'knowledge:list' }
      },
      {
        path: 'knowledge/editor/:id?',
        name: 'KnowledgeEditor',
        component: () => import('@/views/knowledge/Editor.vue'),
        meta: { title: '知识编辑', activeMenu: '/knowledge/list', permission: 'knowledge:list' }
      },
      {
        path: 'ops/statistics',
        name: 'OpsStatistics',
        component: () => import('@/views/ops/Statistics.vue'),
        meta: { title: '运维统计', icon: 'data-analysis', permission: 'ops:statistics' }
      },

      // ============ 产品与设备 ============
      {
        path: 'device/list',
        name: 'DeviceList',
        component: () => import('@/views/device/List.vue'),
        meta: { title: '设备列表', icon: 'list', permission: 'device:list' }
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

      // ============ 数据服务 ============
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
        path: 'report/center',
        name: 'ReportCenter',
        component: () => import('@/views/report/Center.vue'),
        meta: { title: '报表中心', icon: 'document-copy', permission: 'report:center' }
      },

      // ============ 大屏可视化 ============
      {
        path: 'screen',
        name: 'Screen',
        component: () => import('@/views/screen/Index.vue'),
        meta: { title: '可视化大屏', icon: 'pie-chart', permission: 'screen:view' }
      },
      {
        path: 'iot-console',
        name: 'IotConsole',
        component: () => import('@/views/iot-console/Index.vue'),
        meta: { title: 'IoT 控制台', icon: 'cpu', permission: 'iot-console:view' }
      },

      // ============ 系统管理 ============
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
        path: 'system/organization',
        name: 'SystemOrganization',
        component: () => import('@/views/system/Organization.vue'),
        meta: { title: '组织架构', icon: 'share', permission: 'system:organization' }
      },
      {
        path: 'system/dict',
        name: 'SystemDict',
        component: () => import('@/views/system/Dict.vue'),
        meta: { title: '字典管理', icon: 'collection', permission: 'system:dict' }
      },
      {
        path: 'system/log',
        name: 'SystemLog',
        component: () => import('@/views/system/Log.vue'),
        meta: { title: '操作日志', icon: 'document-checked', permission: 'system:log' }
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