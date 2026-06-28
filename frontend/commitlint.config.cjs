// 提交信息必须遵循 Conventional Commits 规范
// 格式: <type>(<scope>): <subject>
// 允许类型: feat / fix / refactor / docs / test / chore / perf / ci / style
// 完整规则: https://commitlint.js.org/#/reference-rules

module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // subject 长度上限 72
    'header-max-length': [2, 'always', 72],
    // type 小写
    'type-case': [2, 'always', 'lower-case'],
    // scope 小写
    'scope-case': [2, 'always', 'lower-case'],
    // 允许的 type
    'type-enum': [
      2,
      'always',
      [
        'feat',
        'fix',
        'refactor',
        'docs',
        'test',
        'chore',
        'perf',
        'ci',
        'style',
        'build',
        'revert'
      ]
    ]
  }
}