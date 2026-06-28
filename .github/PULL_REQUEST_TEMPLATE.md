<!--
感谢你提交 PR!请填写以下 checklist 让 review 更高效。
请删除不适用的条目,不要删除模板整体结构。
-->

## 📝 改动摘要

<!-- 1-3 句话说明这次改动的目的和方案 -->

- 

## 🎯 改动类型 (必选)

- [ ] 新功能 (feat)
- [ ] Bug 修复 (fix)
- [ ] 重构 (refactor)
- [ ] 文档 (docs)
- [ ] 测试 (test)
- [ ] 构建/CI (ci/build)
- [ ] 运维/部署 (chore)
- [ ] 性能优化 (perf)
- [ ] 样式调整 (style)

## 📂 改动文件清单

<!-- 列出主要修改的文件及其作用 -->

- `path/to/file` — 改动说明
- `path/to/another` — 改动说明

## 🔗 关联 Issue

<!-- 关闭/关联的 issue,例如:Closes #123,Refs #456 -->

- 

## ⚠️ Breaking Changes

<!-- 如果有破坏性变更(API 行为变更 / 字段重命名 / 配置项删除),必须列出 -->

- [ ] 无破坏性变更
- [ ] 有破坏性变更(下方说明)

<!-- 描述变更内容 + 迁移指南 -->

## 🧪 测试步骤

<!-- reviewer 复现/验证本次改动的步骤 -->

1. 
2. 
3. 

## ✅ 自检 Checklist (必填)

### 代码质量

- [ ] 提交前已跑 `npm run verify` / `mvn verify` 全绿
- [ ] 新增/修改的代码已写单元测试
- [ ] 注释说明了"为什么"而非"是什么"
- [ ] 无 `console.log` 残留
- [ ] 无 hardcoded 密钥 / 密码 / IP

### 文档

- [ ] 已更新 `docs/architecture.md` 或相关文档(若架构变更)
- [ ] 已更新 API 文档 / Swagger 注解(若 API 变更)
- [ ] 已更新 `.env.example`(若环境变量变更)

### 部署

- [ ] 已更新 `deploy/.env.example`(若配置变更)
- [ ] 已更新 Dockerfile / docker-compose(若镜像变更)
- [ ] 数据库迁移脚本已附(若 schema 变更)

## 📸 截图(可选)

<!-- UI 改动请附 before/after 截图 -->

## 💬 其他

<!-- review 时需要特别注意的点 / 风险 / 遗留问题 -->

-