import request from '@/api/request'

export interface SysMenuTreeVO {
  id: number
  parentId: number
  menuName: string
  menuType: number  // 1=目录 2=菜单 3=按钮
  path?: string
  icon?: string
  sort?: number
  permission?: string
  children?: SysMenuTreeVO[]
}

export function getMenuTree() {
  return request<SysMenuTreeVO[]>({ url: '/system/menu/tree', method: 'get' })
}