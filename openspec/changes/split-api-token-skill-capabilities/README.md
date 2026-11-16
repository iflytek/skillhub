# split-api-token-skill-capabilities

本变更整理 Issue #1049：把 API Token 的技能写入能力从单一 `skill:publish` 拆成“提交、私有发布、管理”，
连同已有的“读取、删除”形成 5 类显式授权，并定义接口映射、组合操作上限、旧 Token／CLI 兼容和反例测试。
多人维护同一技能（#732）与可信成员免人工审核（#1068）不在本变更范围内。
