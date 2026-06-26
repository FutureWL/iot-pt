import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'
import { getToken, removeToken } from '@/utils/auth'
import type { ApiResponse } from '@/types/common'

NProgress.configure({ showSpinner: false })

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

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
    const msg = error?.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

/** 通用请求方法 */
export function request<T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service(config) as any
}

export default service
