/**
 * LoginForm 组件测试示例
 *
 * 演示如何用 @vue/test-utils 测试 Vue 组件
 * (这里 mock 一个假组件,实际项目里替换为真实的登录页)
 */
import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'

// ============================================================
// 测试用组件: 模拟登录页核心交互
// ============================================================
const LoginFormMock = defineComponent({
  name: 'LoginFormMock',
  emits: ['submit'],
  setup(_, { emit }) {
    const username = ref('')
    const password = ref('')
    const loading = ref(false)

    async function handleSubmit() {
      if (!username.value || !password.value) return
      loading.value = true
      try {
        emit('submit', { username: username.value, password: password.value })
      } finally {
        loading.value = false
      }
    }

    return () =>
      h('form', { onSubmit: handleSubmit }, [
        h('input', {
          'data-testid': 'username',
          value: username.value,
          onInput: (e: any) => (username.value = e.target.value)
        }),
        h('input', {
          'data-testid': 'password',
          type: 'password',
          value: password.value,
          onInput: (e: any) => (password.value = e.target.value)
        }),
        h('button', { type: 'submit', 'data-testid': 'submit', disabled: loading.value }, '登录')
      ])
  }
})

describe('LoginForm 组件', () => {
  it('渲染用户名/密码输入框和登录按钮', () => {
    const wrapper = mount(LoginFormMock)
    expect(wrapper.find('[data-testid="username"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="password"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="submit"]').exists()).toBe(true)
  })

  it('提交空表单时不应触发 submit 事件', async () => {
    const onSubmit = vi.fn()
    const wrapper = mount(LoginFormMock, { props: { onSubmit } })

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('填写完整信息后提交应触发 submit 事件并携带参数', async () => {
    const onSubmit = vi.fn()
    const wrapper = mount(LoginFormMock, { props: { onSubmit } })

    await wrapper.find('[data-testid="username"]').setValue('admin')
    await wrapper.find('[data-testid="password"]').setValue('admin123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(onSubmit).toHaveBeenCalledWith({
      username: 'admin',
      password: 'admin123'
    })
  })
})
