# 前端 Lint/Format 基建 — 2026-06-28

## 实施内容

### 1. 工具链 (devDeps)

新增 11 个 devDeps(`npm install -D ... --legacy-peer-deps`):

| 包 | 用途 |
|---|---|
| `eslint@^9` + `@eslint/js` | 静态检查(flat config) |
| `typescript-eslint` | TS 规则(parser + plugin 合一) |
| `eslint-plugin-vue@^9` + `vue-eslint-parser` | Vue 3 SFC 检查 |
| `prettier` | 格式化 |
| `@commitlint/cli` + `@commitlint/config-conventional` | commit 规范校验 |
| `husky` + `lint-staged` | git 钩子 + staged 增量格式化 |

### 2. 配置文件 (6 个新增)

| 文件 | 作用 |
|---|---|
| `frontend/eslint.config.js` | ESLint 9 flat config:TS + Vue 3 推荐规则 + 项目级覆盖 |
| `frontend/.prettierrc.json` | Prettier:single quote / no semi / printWidth 100 |
| `frontend/.prettierignore` | 排除 dist/node_modules/auto-imports.d.ts 等 |
| `frontend/commitlint.config.cjs` | Conventional Commits + 11 个允许 type + header ≤ 72 |
| `frontend/.lintstagedrc.json` | staged 文件:JS/TS/Vue 跑 eslint --fix + prettier;JSON/MD/CSS 跑 prettier |
| `frontend/.editorconfig` | 2 空格 / LF / UTF-8 / 去尾空 |

### 3. npm scripts 新增

```json
{
  "lint":          "eslint .",
  "lint:fix":      "eslint . --fix",
  "format":        "prettier --write \"src/**/*.{js,ts,vue,json,scss,md,yml,yaml}\" ...",
  "format:check":  "prettier --check ...",
  "verify":        "npm run type-check && npm run lint && npm run test:run && npm run e2e"
}
```

`prepare: husky` 由 husky 9 自动加入(下次 `npm install` 时跑 `husky init`)。

### 4. 未激活: git hooks

`husky` 工具链已就位,但**未配置** `core.hooksPath`,即 git commit 不会自动跑 `lint-staged` / `commitlint`。

**原因**: Self-modification 安全护栏拦截了 `git config core.hooksPath frontend/.husky`(此命令会让 agent 自身的 commit 流程改变)。

**激活方式**(任选其一,需用户执行):

```bash
# 方案 A: 让 husky 自动激活
cd frontend && npx husky init
# 然后编辑 frontend/.husky/pre-commit 改为:
#   npx lint-staged
# 编辑 frontend/.husky/commit-msg 改为:
#   npx --no -- commitlint --edit "$1"

# 方案 B: CI 强制跑 lint(无需本地钩子)
# 在 .github/workflows/pr-check.yml 加:
#   - run: npm run lint
#   - run: npm run format:check
```

推荐 **方案 B**:CI 跑 lint,本地跑 `npm run lint:fix` / `npm run format`。避免 commit 钩子带来的体验问题(慢、卡死等)。

---

## Baseline 数据 (修复前)

跑 `npx eslint .`(原始规则)结果:

```
✖ 3084 problems (84 errors, 3000 warnings)
0 errors and 2262 warnings potentially fixable with the `--fix` option.
```

### 84 errors 分类(降级为后续 sprint)

| 类型 | 数量 | 原因 |
|---|---|---|
| `vue/multi-word-component-names` | 13+ | 项目 view 惯例单字命名(`List.vue`/`Index.vue`/`User.vue` 等),规则与惯例冲突 |
| `no-empty` | 30 | 空 catch / placeholder 块,业务上常见(API 兜底) |
| `no-useless-assignment` | 15 | 写代码时遗留 |
| `no-loss-of-precision` | 2 | 大数字字面量 |
| `@typescript-eslint/ban-ts-comment` | 1 | main.ts 用了 `@ts-ignore`(应改 `@ts-expect-error`) |

**降级方案**:`multi-word-component-names` 和 `no-empty` 调整为 `warn`。
**当前阻塞**:`eslint.config.js` 被 config-protection 钩子保护,Edit 不允许;**降级**留待用户手动调整。

### 跑 `npx eslint . --fix` 后

```
✖ 126 problems (84 errors, 42 warnings)
```

2958 个问题(几乎全是格式化 + 简单修复)已自动修。

### 最终 lint 状态

- **84 errors** 留 TODO(规则需降级 + 部分代码手修)
- **42 warnings** 留 TODO(类型未使用、@ts-expect-error 等)

---

## 验证

| 命令 | 结果 |
|---|---|
| `npx eslint . --fix` | 自动修 2958 个问题,剩余 126(84 errors + 42 warnings) |
| `npx prettier --check ...` | 待跑(format 实施后可验证) |
| `npx vitest run` | 63/63 仍通过(本次未改测试代码) |
| `npx vue-tsc --noEmit` | 0 错误(已验证) |

---

## 后续 TODO(留给后续 sprint)

1. 跑 `npx eslint . --fix` 一次性把 2262 个 auto-fixable warnings 全部消化(本会话已做)
2. 修 84 个 errors(30 个 `no-empty` + 13 个 `multi-word` + 41 个其它)
3. 决定是否激活 husky git hooks:
   - 选 A(本地 lint-staged + commit-msg):需用户授权 `git config core.hooksPath`
   - 选 B(CI-only):加 `.github/workflows/pr-check.yml` 跑 `npm run lint`
4. 迁移到 `eslint-plugin-vue` 9.x 的 strict 规则(项目级 warn → error 渐进)
5. 加 `stylelint` 检查 SCSS 语法
6. 加 `markdownlint` 检查 .md 文档