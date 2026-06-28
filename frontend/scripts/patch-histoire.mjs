/**
 * Patch Histoire v1 beta bundling bug
 *
 * 问题:
 *   Histoire 内部用 pathToFileURL() 把 vendor 模块转成 file:// URL,
 *   Rollup 在 bundle 阶段无法解析 file:// URL,导致 build 失败。
 *
 * 修复:
 *   修改 `node_modules/histoire/dist/node/util/vendors.js`,
 *   让 getInjectedImport() 绝对路径分支直接返回原路径,
 *   Vite alias 会把 @histoire/vendors/vue 映射到普通模块路径。
 *
 * 检测逻辑:
 *   1. 已 patch 标记 '// patched-by-script' → 跳过
 *   2. 两分支返回值一致(已修) → 标记并跳过
 *   3. 包含 pathToFileURL → 执行 patch
 *   4. 其他情况 → Histoire 版本可能已变,warn
 *
 * 注:这是 Histoire 上游 bug 的临时 workaround。
 *   跟踪:https://github.com/histoire-dev/histoire/issues
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const target = resolve(__dirname, '..', 'node_modules', 'histoire', 'dist', 'node', 'util', 'vendors.js')

if (!existsSync(target)) {
  console.log('[patch-histoire] histoire not installed, skipping')
  process.exit(0)
}

const original = readFileSync(target, 'utf-8')

// 1. 已 patch 标记
if (original.includes('// patched-by-script')) {
  console.log('[patch-histoire] already patched (marker found), skipping')
  process.exit(0)
}

// 2. 两分支返回值一致(已经手动 sed 过)
const ifBranch = /if \(path\.isAbsolute\(id\)\) \{[\s\S]*?return (.*?);[\s\S]*?\}/m.exec(original)
const elseBranch = /else \{[\s\S]*?return (.*?);[\s\S]*?\}/m.exec(original)
if (ifBranch && elseBranch && ifBranch[1].trim() === elseBranch[1].trim()) {
  // 已修但缺标记,加标记
  const marked = original.replace(
    ifBranch[1],
    `${ifBranch[1]} // patched-by-script: 绝对路径分支返回原路径,vite alias 接管`
  )
  writeFileSync(target, marked, 'utf-8')
  console.log('[patch-histoire] ✅ already fixed (two branches match), added marker')
  process.exit(0)
}

// 3. 包含 pathToFileURL → 执行 patch
if (original.includes('pathToFileURL(id).href')) {
  const patched = original.replace(
    /return JSON\.stringify\(pathToFileURL\(id\)\.href\);/g,
    "return JSON.stringify(id); // patched-by-script: 去掉 file:// 转换,改用 vite alias"
  )
  writeFileSync(target, patched, 'utf-8')
  console.log('[patch-histoire] ✅ patched pathToFileURL → JSON.stringify(id)')
  process.exit(0)
}

// 4. Histoire 版本可能已变
console.warn('[patch-histoire] ⚠ patch pattern not found — Histoire version may have changed')
process.exit(0)