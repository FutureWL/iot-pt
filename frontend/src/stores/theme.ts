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

  function init(): void {
    mode.value = readModeFromStorage()
    resolved.value = resolveFromMode(mode.value)
    applyClass(resolved.value)
  }

  return { mode, resolved, isDark, setMode, init }
})
