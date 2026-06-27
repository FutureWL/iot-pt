import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore, type ThemeMode } from '../theme'

// 内存版 localStorage
const storage = new Map<string, string>()
const fakeStorage = {
  getItem: (k: string) => storage.get(k) ?? null,
  setItem: (k: string, v: string) => { storage.set(k, v) },
  removeItem: (k: string) => { storage.delete(k) },
  clear: () => { storage.clear() },
  key: () => null,
  length: 0
}

// 简易 matchMedia mock,允许测试中动态切换 + 模拟 change 事件
type Listener = (e: { matches: boolean }) => void
let currentMatch = false
const listeners: Listener[] = []
const fakeMq: MediaQueryList = {
  get matches() { return currentMatch },  // getter: 每次访问读 currentMatch
  media: '(prefers-color-scheme: dark)',
  onchange: null,
  addEventListener: (_: string, l: Listener) => { listeners.push(l) },
  removeEventListener: (_: string, l: Listener) => {
    const i = listeners.indexOf(l)
    if (i >= 0) listeners.splice(i, 1)
  },
  addListener: (l: Listener) => listeners.push(l),
  removeListener: (l: Listener) => {
    const i = listeners.indexOf(l)
    if (i >= 0) listeners.splice(i, 1)
  },
  dispatchEvent: () => true
} as unknown as MediaQueryList

// 触发所有监听器,模拟系统主题切换
function emitMatchMediaChange(matches: boolean) {
  currentMatch = matches
  for (const l of listeners) l({ matches })
}

beforeEach(() => {
  vi.stubGlobal('localStorage', fakeStorage)
  vi.stubGlobal('matchMedia', () => fakeMq)
  storage.clear()
  document.documentElement.classList.remove('dark')
  currentMatch = false
  listeners.length = 0
  setActivePinia(createPinia())
})

describe('useThemeStore — init()', () => {
  it('空 localStorage 时,mode = system,resolved 跟随 matchMedia(false)', () => {
    currentMatch = false
    const store = useThemeStore()
    store.init()
    expect(store.mode).toBe('system')
    expect(store.resolved).toBe('light')
    expect(store.isDark).toBe(false)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('空 localStorage 时,matchMedia 偏好暗色,resolved = dark,html.dark class 挂上', () => {
    currentMatch = true
    const store = useThemeStore()
    store.init()
    expect(store.resolved).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('localStorage 有合法 dark 值,mode = dark,resolved = dark', () => {
    storage.set('iot-theme', 'dark')
    const store = useThemeStore()
    store.init()
    expect(store.mode).toBe('dark')
    expect(store.resolved).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('localStorage 有合法 light 值,mode = light,html.dark class 移除', () => {
    storage.set('iot-theme', 'light')
    document.documentElement.classList.add('dark')
    const store = useThemeStore()
    store.init()
    expect(store.mode).toBe('light')
    expect(store.resolved).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('localStorage 有非法值(比如 "rainbow"),兜底为 system', () => {
    storage.set('iot-theme', 'rainbow')
    const store = useThemeStore()
    store.init()
    expect(store.mode).toBe('system')
  })
})

describe('useThemeStore — setMode()', () => {
  it('setMode("dark") 后 mode=dark,resolved=dark,html.dark 挂上,storage 写入', () => {
    currentMatch = false
    const store = useThemeStore()
    store.init()
    store.setMode('dark')
    expect(store.mode).toBe('dark')
    expect(store.resolved).toBe('dark')
    expect(store.isDark).toBe(true)
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(storage.get('iot-theme')).toBe('dark')
  })

  it('setMode("light") 后移除 html.dark', () => {
    currentMatch = true
    const store = useThemeStore()
    store.init()
    expect(document.documentElement.classList.contains('dark')).toBe(true) // system -> dark
    store.setMode('light')
    expect(store.mode).toBe('light')
    expect(store.resolved).toBe('light')
    expect(store.isDark).toBe(false)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(storage.get('iot-theme')).toBe('light')
  })

  it('setMode("system") 时 resolved 跟随 matchMedia', () => {
    currentMatch = true
    const store = useThemeStore()
    store.init()
    store.setMode('light')                       // 先锁定 light
    store.setMode('system')                      // 再切回 system
    expect(store.mode).toBe('system')
    expect(store.resolved).toBe('dark')           // 跟随 system
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })
})

describe('useThemeStore — system 主题变化响应', () => {
  it('mode=system 时,matchMedia 切换 dark→light,resolved 跟随变化', () => {
    currentMatch = true
    const store = useThemeStore()
    store.init()
    expect(store.resolved).toBe('dark')

    emitMatchMediaChange(false)
    expect(store.resolved).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('mode=light 时,matchMedia 切换不影响 resolved(用户已锁定)', () => {
    currentMatch = true
    const store = useThemeStore()
    store.init()
    store.setMode('light')
    expect(store.resolved).toBe('light')

    emitMatchMediaChange(true)                  // 系统切到暗色
    expect(store.resolved).toBe('light')        // 用户锁定 light,不响应
    expect(store.mode).toBe('light')
  })

  it('mode=dark 时,matchMedia 切换不影响 resolved(用户已锁定)', () => {
    currentMatch = false
    const store = useThemeStore()
    store.init()
    store.setMode('dark')
    expect(store.resolved).toBe('dark')

    emitMatchMediaChange(false)                 // 系统切到亮色
    expect(store.resolved).toBe('dark')         // 用户锁定 dark,不响应
    expect(store.mode).toBe('dark')
  })
})

describe('useThemeStore — init/dispose refCount(防重复注册监听)', () => {
  it('多次 init 只注册一个 matchMedia 监听器', () => {
    const store1 = useThemeStore()
    const store2 = useThemeStore()
    store1.init()
    store2.init()                                // 第二个 init 不应重复注册
    expect(listeners.length).toBe(1)

    emitMatchMediaChange(true)
    expect(store1.resolved).toBe('dark')
    expect(store2.resolved).toBe('dark')
  })

  it('refCount 降到 0 时才真正注销监听器', () => {
    const store1 = useThemeStore()
    const store2 = useThemeStore()
    store1.init()
    store2.init()
    expect(listeners.length).toBe(1)

    store1.dispose()
    expect(listeners.length).toBe(1)             // 还有一个引用,保留监听

    emitMatchMediaChange(false)
    expect(store2.resolved).toBe('light')        // 监听仍然有效

    store2.dispose()
    expect(listeners.length).toBe(0)             // 全部释放,才注销
  })

  it('dispose 后再 emit,resolved 不再变化', () => {
    currentMatch = true
    const store = useThemeStore()
    store.init()
    store.dispose()

    emitMatchMediaChange(false)                  // 监听已注销
    expect(store.resolved).toBe('dark')          // 没有变化
  })
})