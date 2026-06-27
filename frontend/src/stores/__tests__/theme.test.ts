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

// 简易 matchMedia mock,允许测试中动态切换
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
