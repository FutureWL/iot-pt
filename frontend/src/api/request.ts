import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'
import { getToken, removeToken } from '@/utils/auth'
import type { ApiResponse } from '@/types/common'

NProgress.configure({ showSpinner: false })

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

/**
 * 识别 Spring Boot 的 NoResourceFoundException(后端未注册的 API 路径 fallback)。
 * 状态码 404 + 响应体 message 以 "No static resource" 开头。
 * 业务层通常已 .catch() 降级处理,这里用于拦截器静默该异常,避免重复提示用户。
 */
export function isSpringNoStaticResourceError(error: unknown, derivedMsg?: string): boolean {
  const msg = derivedMsg
    ?? (error as any)?.response?.data?.message
    ?? (error as any)?.message
    ?? ''
  return msg.startsWith('No static resource')
}

const service: AxiosInstance = axios.create({
  baseURL,
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' }
})

// 请求拦截
service.interceptors.request.use(
  (config) => {
    NProgress.start()
    const token = getToken()
    if (token) {
      config.headers = config.headers || {}
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    NProgress.done()
    return Promise.reject(error)
  }
)

// 响应拦截
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    NProgress.done()
    const res = response.data

    // 二进制流(下载)直接放行
    if (response.config.responseType === 'blob') {
      return response
    }

    if (res.code === 200) {
      return res as any
    }

    // 401 - 重新登录
    if (res.code === 401) {
      ElMessageBox.confirm('登录已过期,请重新登录', '提示', {
        confirmButtonText: '重新登录',
        cancelButtonText: '取消',
        type: 'warning'
      })
        .then(() => {
          const userStore = useUserStore()
          userStore.reset()
          removeToken()
          location.href = '/#/login'
        })
        .catch(() => {})
      return Promise.reject(new Error(res.message || '未授权'))
    }

    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    NProgress.done()
    const msg: string = error?.response?.data?.message || error.message || '网络异常'

    if (isSpringNoStaticResourceError(error, msg)) {
      // Spring "No static resource" 兜底异常(后端未注册的 API 路径):
      //   业务层通常已有 .catch() 降级,这里静默不再弹红字,但仍 reject 让 catch 接管
      return Promise.reject(error)
    }

    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

/** 通用请求方法 */
export function request<T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service(config) as any
}

export default service
