/**
 * ModalForm 测试 — 用 template-string stubs 隔离 el-dialog / el-form
 * 内部实现,验证 ModalForm 的 props 透传 + 事件契约 + slot 渲染。
 *
 * 使用 template stub 而非简单 boolean stub:
 *   - 保留 slot 渲染能力
 *   - 暴露必要的 DOM 属性供测试查询
 *   - 可以 emit 真实事件让父组件响应
 */
import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ModalForm from '../ModalForm.vue'

interface FormData extends Record<string, unknown> {
  username?: string
  email?: string
}

/**
 * template-string stubs.
 * 关键:template 里要显式渲染 <slot/> 和 <slot name="footer"/>,
 * 否则父组件传进来的 slot 内容看不见。
 */
const elDialogStub = {
  template: `
    <div
      v-if="modelValue"
      class="el-dialog-stub"
      :data-testid="'el-dialog'"
      :data-title="title"
      :data-width="width"
      :data-close-on-click-modal="closeOnClickModal"
      :data-destroy-on-close="destroyOnClose"
    >
      <div class="el-dialog-body"><slot /></div>
      <div v-if="$slots.footer" class="el-dialog-footer"><slot name="footer" /></div>
    </div>
  `,
  props: ['modelValue', 'title', 'width', 'closeOnClickModal', 'destroyOnClose', 'beforeClose'],
  emits: ['update:modelValue', 'opened', 'closed']
}

const elFormStub = {
  template: `<form data-testid="el-form" :data-label-width="labelWidth"><slot /></form>`,
  props: ['model', 'rules', 'labelWidth'],
  methods: {
    // 默认通过校验;具体测试用例可通过 wrapper.vm 重写
    async validate() { return true },
    clearValidate() { /* noop */ }
  }
}

/** el-button 必须渲染 slot 才能看到按钮文字 — 覆盖全局 boolean stub */
const elButtonStub = {
  template: `<button :disabled="disabled" :class="{ 'is-loading': loading, 'is-disabled': disabled }"><slot /></button>`,
  props: ['disabled', 'loading', 'type']
}

function makeWrapper(props: Record<string, unknown> = {}) {
  return mount(ModalForm, {
    props: {
      visible: true,
      title: '测试对话框',
      model: { username: '', email: '' } as FormData,
      ...props
    },
    slots: {
      default: '<input data-testid="form-item" />'
    },
    global: {
      stubs: {
        'el-dialog': elDialogStub,
        'el-form': elFormStub,
        'el-button': elButtonStub
      }
    }
  })
}

describe('ui/ModalForm — 基础渲染', () => {
  it('visible=true 渲染 dialog', () => {
    const w = makeWrapper({ visible: true })
    expect(w.find('.el-dialog-stub').exists()).toBe(true)
  })

  it('visible=false 不渲染 dialog', () => {
    const w = makeWrapper({ visible: false })
    expect(w.find('.el-dialog-stub').exists()).toBe(false)
  })

  it('title 透传到 el-dialog', () => {
    const w = makeWrapper({ title: '新建用户' })
    expect(w.find('.el-dialog-stub').attributes('data-title')).toBe('新建用户')
  })

  it('width 数字 → 转 px 字符串', () => {
    const w = makeWrapper({ width: 520 })
    expect(w.find('.el-dialog-stub').attributes('data-width')).toBe('520px')
  })

  it('width 字符串 → 直接透传', () => {
    const w = makeWrapper({ width: '80%' })
    expect(w.find('.el-dialog-stub').attributes('data-width')).toBe('80%')
  })

  it('默认 width=640px', () => {
    const w = makeWrapper()
    expect(w.find('.el-dialog-stub').attributes('data-width')).toBe('640px')
  })

  it('closeOnClickModal 透传', () => {
    const w = makeWrapper({ closeOnClickModal: false })
    expect(w.find('.el-dialog-stub').attributes('data-close-on-click-modal')).toBe('false')
  })

  it('destroyOnClose 默认 true', () => {
    const w = makeWrapper()
    expect(w.find('.el-dialog-stub').attributes('data-destroy-on-close')).toBe('true')
  })
})

describe('ui/ModalForm — slot 内容', () => {
  it('default slot 内容渲染到 dialog 内', () => {
    const w = makeWrapper()
    expect(w.find('[data-testid="form-item"]').exists()).toBe(true)
  })

  it('el-form 渲染', () => {
    const w = makeWrapper()
    expect(w.find('[data-testid="el-form"]').exists()).toBe(true)
  })

  it('label-width 透传到 el-form', () => {
    const w = makeWrapper({ labelWidth: '120px' })
    expect(w.find('[data-testid="el-form"]').attributes('data-label-width')).toBe('120px')
  })
})

describe('ui/ModalForm — footer 按钮', () => {
  it('footer 内有取消按钮(默认文字)', () => {
    const w = makeWrapper()
    const cancelBtn = w.findAll('button').find(b => b.text().includes('取消'))
    expect(cancelBtn).toBeTruthy()
  })

  it('footer 内有保存按钮(默认文字)', () => {
    const w = makeWrapper()
    const submitBtn = w.findAll('button').find(b => b.text().includes('保存'))
    expect(submitBtn).toBeTruthy()
  })

  it('cancelText 自定义生效', () => {
    const w = makeWrapper({ cancelText: '放弃编辑' })
    expect(w.html()).toContain('放弃编辑')
  })

  it('submitText 自定义生效', () => {
    const w = makeWrapper({ submitText: '立即创建' })
    expect(w.html()).toContain('立即创建')
  })

  it('loading=true 时取消按钮 disabled', () => {
    const w = makeWrapper({ loading: true })
    const cancelBtn = w.findAll('button').find(b => b.text().includes('取消'))!
    expect(cancelBtn.attributes('disabled')).toBeDefined()
  })

  it('loading=true 时保存按钮 is-loading 类', () => {
    const w = makeWrapper({ loading: true })
    const submitBtn = w.findAll('button').find(b => b.text().includes('保存'))!
    expect(submitBtn.classes()).toContain('is-loading')
  })
})

describe('ui/ModalForm — v-model:visible', () => {
  it('点击取消 → emit update:visible false', async () => {
    const w = makeWrapper({ visible: true })
    const cancelBtn = w.findAll('button').find(b => b.text().includes('取消'))!
    await cancelBtn.trigger('click')
    expect(w.emitted('update:visible')?.[0]).toEqual([false])
  })

  it('点击取消 → 同时 emit cancel', async () => {
    const w = makeWrapper({ visible: true })
    const cancelBtn = w.findAll('button').find(b => b.text().includes('取消'))!
    await cancelBtn.trigger('click')
    expect(w.emitted('cancel')).toBeTruthy()
  })
})

describe('ui/ModalForm — 提交', () => {
  it('点击提交 → validate 通过则 emit submit', async () => {
    const w = makeWrapper({
      model: { username: 'admin', email: 'a@b.c' } as FormData
    })
    const submitBtn = w.findAll('button').find(b => b.text().includes('保存'))!
    await submitBtn.trigger('click')
    await flushPromises()
    expect(w.emitted('submit')).toBeTruthy()
  })

  it('点击提交 → validate 失败则不 emit submit', async () => {
    const w = makeWrapper({
      model: { username: '', email: '' } as FormData
    })
    // 覆盖 stub 的 validate 让它返回 false
    const formEl = w.find('[data-testid="el-form"]').element as any
    formEl.validate = vi.fn().mockResolvedValue(false)
    // 通过组件实例强制替换 formRef.value.validate
    const exposed = (w.vm as any).$.exposed
    if (exposed.formRef && exposed.formRef.value) {
      exposed.formRef.value.validate = vi.fn().mockResolvedValue(false)
    }
    const submitBtn = w.findAll('button').find(b => b.text().includes('保存'))!
    await submitBtn.trigger('click')
    await flushPromises()
    expect(w.emitted('submit')).toBeFalsy()
  })
})

describe('ui/ModalForm — defineExpose', () => {
  it('暴露 validate 函数', () => {
    const w: VueWrapper = makeWrapper()
    const exposed = (w.vm as any).$.exposed
    expect(typeof exposed.validate).toBe('function')
  })

  it('暴露 clearValidate 函数', () => {
    const w: VueWrapper = makeWrapper()
    const exposed = (w.vm as any).$.exposed
    expect(typeof exposed.clearValidate).toBe('function')
  })

  it('暴露 formRef 对象', () => {
    const w: VueWrapper = makeWrapper()
    const exposed = (w.vm as any).$.exposed
    expect(exposed.formRef).toBeDefined()
  })
})

describe('ui/ModalForm — beforeClose', () => {
  it('beforeClose 是 prop', () => {
    const beforeClose = vi.fn(() => true)
    const w = makeWrapper({ beforeClose })
    expect((w.vm as any).beforeClose).toBe(beforeClose)
  })
})