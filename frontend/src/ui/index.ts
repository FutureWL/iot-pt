/**
 * 设计系统层统一导出 (barrel export)
 *
 * 使用方式:
 *   import { KpiCard, StatusTag, CrudList } from '@/ui'
 *
 * 组件按发布顺序分组:
 * - 基础原子组件: EmptyState / StatusTag / KpiCard / DescriptionList
 * - CRUD 三件套: QueryBar / DataTable / Pager
 * - CRUD 综合: CrudList
 */

// 基础原子组件
export { default as EmptyState } from './EmptyState.vue'
export { default as StatusTag } from './StatusTag.vue'
export { default as KpiCard } from './KpiCard.vue'
export { default as DescriptionList } from './DescriptionList.vue'

// CRUD 三件套
export { default as QueryBar } from './QueryBar.vue'
export { default as DataTable } from './DataTable.vue'
export { default as Pager } from './Pager.vue'

// CRUD 综合
export { default as CrudList } from './CrudList.vue'

export {}
