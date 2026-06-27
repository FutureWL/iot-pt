import { describe, it, expect } from 'vitest'
import { isSpringNoStaticResourceError } from '@/api/request'

describe('api/request — isSpringNoStaticResourceError', () => {
  it('error.response.data.message 以 "No static resource" 开头 → 返回 true', () => {
    const err = {
      response: { status: 404, data: { message: 'No static resource workorder/stats.' } }
    }
    expect(isSpringNoStaticResourceError(err)).toBe(true)
  })

  it('error.response.data.message 仅含 "No static resource"(无路径) → 返回 true', () => {
    const err = {
      response: { status: 404, data: { message: 'No static resource' } }
    }
    expect(isSpringNoStaticResourceError(err)).toBe(true)
  })

  it('error.message 含 "No static resource"(无 response) → 返回 true', () => {
    const err = { message: 'No static resource api/foo' }
    expect(isSpringNoStaticResourceError(err)).toBe(true)
  })

  it('derivedMsg 参数优先于 error.message', () => {
    const err = { message: '无关错误' }
    expect(isSpringNoStaticResourceError(err, 'No static resource api/x')).toBe(true)
  })

  it('derivedMsg 不是 No static resource 时,以 error.message 为准', () => {
    const err = { message: 'No static resource api/x' }
    expect(isSpringNoStaticResourceError(err, '无关 derived')).toBe(false)
  })

  it('普通 Error 对象(message 不含 "No static resource") → 返回 false', () => {
    expect(isSpringNoStaticResourceError(new Error('Network Error'))).toBe(false)
    expect(isSpringNoStaticResourceError(new Error('Request failed with status code 500'))).toBe(false)
  })

  it('error 含 response.data.message 但不是 "No static resource" 开头 → 返回 false', () => {
    const err = {
      response: { status: 404, data: { message: 'Not Found' } }
    }
    expect(isSpringNoStaticResourceError(err)).toBe(false)
  })

  it('error 含 message 但不是 "No static resource" 开头 → 返回 false', () => {
    expect(isSpringNoStaticResourceError({ message: 'static resource not found' })).toBe(false)
    expect(isSpringNoStaticResourceError({ message: 'NoResourceFoundException' })).toBe(false)
  })

  it('null / undefined / 空对象 → 返回 false(不抛错)', () => {
    expect(isSpringNoStaticResourceError(null)).toBe(false)
    expect(isSpringNoStaticResourceError(undefined)).toBe(false)
    expect(isSpringNoStaticResourceError({})).toBe(false)
  })

  it('derivedMsg 为空字符串且 error 也没 message → 返回 false', () => {
    expect(isSpringNoStaticResourceError({})).toBe(false)
    expect(isSpringNoStaticResourceError({}, '')).toBe(false)
  })
})