/**
 * 设计系统层统一导出 (barrel export)
 *
 * 使用方式:
 *   import { KpiCard, StatusTag, CrudList, type ColumnDef } from '@/ui'
 */

// 基础原子组件
export { default as EmptyState } from './EmptyState.vue'
export { default as StatusTag } from './StatusTag.vue'
export { default as KpiCard } from './KpiCard.vue'
export { default as DescriptionList } from './DescriptionList.vue'

// CRUD 三件套
export { default as QueryBar } from './QueryBar.vue'
export type { FilterItem } from './QueryBar.vue'
export { default as DataTable } from './DataTable.vue'
export type { ColumnDef } from './DataTable.vue'
export { default as Pager } from './Pager.vue'

// CRUD 综合
export { default as CrudList } from './CrudList.vue'

export {}
