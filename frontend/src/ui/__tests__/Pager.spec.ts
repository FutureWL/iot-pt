/**
 * Pager 组件测试
 *
 * 设计目标:统一 6+ 处 el-pagination 重复
 *
 * 接口契约:
 *   - props.current: number
 *   - props.size: number
 *   - props.total: number
 *   - props.pageSizes?: number[]                 // 默认 [10, 20, 50, 100]
 *   - props.layout?: string                      // 默认 'total, sizes, prev, pager, next, jumper'
 *   - v-model:current / v-model:size             // 支持 v-model 双向绑定
 *
 * 用法:
 *   <Pager v-model:current="query.pageNum" v-model:size="query.pageSize" :total="total" />
 */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import Pager from '../Pager.vue'

const ElPaginationStub = defineComponent({
  name: 'ElPagination',
  props: ['currentPage', 'pageSize', 'total', 'pageSizes', 'layout'],
  emits: ['update:currentPage', 'update:pageSize', 'current-change', 'size-change'],
  setup(props, { emit, attrs }) {
    return () =>
      h('div', {
        'data-testid': 'el-pagination',
        'data-current': attrs['currentPage'] ?? props.currentPage,
        'data-size': attrs['pageSize'] ?? props.pageSize,
        'data-total': props.total,
        'data-layout': props.layout,
        'data-page-sizes': (props.pageSizes ?? []).join(',')
      }, [
        h('button', {
          'data-testid': 'btn-prev',
          onClick: () => emit('current-change', (Number(attrs['currentPage'] ?? props.currentPage) - 1))
        }, 'prev'),
        h('button', {
          'data-testid': 'btn-next',
          onClick: () => emit('current-change', (Number(attrs['currentPage'] ?? props.currentPage) + 1))
        }, 'next'),
        h('button', {
          'data-testid': 'btn-size',
          onClick: () => emit('size-change', 50)
        }, 'change-size')
      ])
  }
})

interface PagerProps {
  current: number
  size: number
  total: number
  pageSizes?: number[]
  layout?: string
}

function mountPg(props: PagerProps) {
  return mount(Pager, {
    props,
    global: {
      stubs: {
        'el-pagination': ElPaginationStub
      }
    }
  })
}

describe('ui/Pager', () => {
  it('将 current / size / total 透传到 el-pagination', () => {
    const wrapper = mountPg({ current: 1, size: 10, total: 100 })
    const pg = wrapper.find('[data-testid="el-pagination"]')
    expect(pg.attributes('data-current')).toBe('1')
    expect(pg.attributes('data-size')).toBe('10')
    expect(pg.attributes('data-total')).toBe('100')
  })

  it('默认 layout 为 "total, sizes, prev, pager, next, jumper"', () => {
    const wrapper = mountPg({ current: 1, size: 10, total: 100 })
    expect(wrapper.find('[data-testid="el-pagination"]').attributes('data-layout'))
      .toBe('total, sizes, prev, pager, next, jumper')
  })

  it('自定义 layout 生效', () => {
    const wrapper = mountPg({ current: 1, size: 10, total: 100, layout: 'prev, pager, next' })
    expect(wrapper.find('[data-testid="el-pagination"]').attributes('data-layout'))
      .toBe('prev, pager, next')
  })

  it('默认 pageSizes = [10, 20, 50, 100]', () => {
    const wrapper = mountPg({ current: 1, size: 10, total: 100 })
    expect(wrapper.find('[data-testid="el-pagination"]').attributes('data-page-sizes'))
      .toBe('10,20,50,100')
  })

  it('自定义 pageSizes 生效', () => {
    const wrapper = mountPg({ current: 1, size: 10, total: 100, pageSizes: [5, 15, 30] })
    expect(wrapper.find('[data-testid="el-pagination"]').attributes('data-page-sizes'))
      .toBe('5,15,30')
  })

  it('点击 next 触发 update:current 事件', async () => {
    const onUpdateCurrent = vi.fn()
    const wrapper = mount(Pager, {
      props: { current: 1, size: 10, total: 100, 'onUpdate:current': onUpdateCurrent },
      global: { stubs: { 'el-pagination': ElPaginationStub } }
    })
    await wrapper.find('[data-testid="btn-next"]').trigger('click')
    expect(onUpdateCurrent).toHaveBeenCalledWith(2)
  })

  it('点击 size-change 触发 update:size 事件', async () => {
    const onUpdateSize = vi.fn()
    const wrapper = mount(Pager, {
      props: { current: 1, size: 10, total: 100, 'onUpdate:size': onUpdateSize },
      global: { stubs: { 'el-pagination': ElPaginationStub } }
    })
    await wrapper.find('[data-testid="btn-size"]').trigger('click')
    expect(onUpdateSize).toHaveBeenCalledWith(50)
  })
})
