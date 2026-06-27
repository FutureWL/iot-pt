import { defineStore } from 'pinia'
import { ref, computed, type Ref, type ComputedRef } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'

const STORAGE_KEY = 'iot-theme'
const HTML_CLASS = 'dark'
const MEDIA_QUERY = '(prefers-color-scheme: dark)'

function readModeFromStorage(): ThemeMode {
  let raw: string | null = null
  try {
    raw = localStorage.getItem(STORAGE_KEY)
  } catch {
    return 'system'
  }
  return raw === 'light' || raw === 'dark' || raw === 'system' ? raw : 'system'
}

function writeModeToStorage(mode: ThemeMode): void {
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    /* quota / privacy mode — ignore */
  }
}

function readSystemPrefers(): ResolvedTheme {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return 'light'
  }
  return window.matchMedia(MEDIA_QUERY).matches ? 'dark' : 'light'
}

function resolveFromMode(mode: ThemeMode): ResolvedTheme {
  return mode === 'system' ? readSystemPrefers() : mode
}

function applyClass(resolved: ResolvedTheme): void {
  document.documentElement.classList.toggle(HTML_CLASS, resolved === 'dark')
}

export const useThemeStore = defineStore('theme', () => {
  const mode: Ref<ThemeMode> = ref<ThemeMode>('system')
  const resolved: Ref<ResolvedTheme> = ref<ResolvedTheme>('light')

  const isDark: ComputedRef<boolean> = computed(() => resolved.value === 'dark')

  function setMode(m: ThemeMode): void {
    mode.value = m
    resolved.value = resolveFromMode(m)
    applyClass(resolved.value)
    writeModeToStorage(m)
  }

  // ---- 系统主题监听(仅当 mode === 'system' 时响应) ----
  // 模块级单例:整个应用只注册一个监听器,即使多个组件 useThemeStore() 也不重复绑定
  let mediaQuery: MediaQueryList | null = null
  let listener: ((e: MediaQueryListEvent) => void) | null = null
  let refCount = 0

  function onSystemThemeChange(): void {
    if (mode.value === 'system') {
      resolved.value = resolveFromMode('system')
      applyClass(resolved.value)
    }
  }

  function init(): void {
    mode.value = readModeFromStorage()
    resolved.value = resolveFromMode(mode.value)
    applyClass(resolved.value)

    refCount++
    if (refCount === 1 && typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      mediaQuery = window.matchMedia(MEDIA_QUERY)
      listener = onSystemThemeChange
      mediaQuery.addEventListener('change', listener)
    }
  }

  function dispose(): void {
    refCount = Math.max(0, refCount - 1)
    if (refCount === 0 && mediaQuery && listener) {
      mediaQuery.removeEventListener('change', listener)
      mediaQuery = null
      listener = null
    }
  }

  return { mode, resolved, isDark, setMode, init, dispose }
})