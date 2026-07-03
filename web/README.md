# Fast Knowledge — Web 前端

Vue 3 + Vite + Element Plus 管理界面。

> **产品定位**：[产品说明.md](../docs/产品说明.md) — 中小企业单实例私有化知识库，非 SaaS 多租户。  
> 接口契约：[api.md](../docs/api.md)

## 页面与路由

| 页面 | 路径 | 权限 |
|------|------|------|
| 概览 | `/dashboard` | 登录 |
| 知识库 | `/kbs`、`/kbs/:id` | 登录 |
| 智能检索 / 问答 / 对话 / 写文档 | `/search`、`/qa`、`/chat`、`/writer` | 登录 |
| 设置与隐私 | `/settings` | 登录 |
| **大模型配置** | `/settings/llm` | 管理员 |
| 用户管理 | `/users` | 管理员 |

## 开发

```powershell
cd web
npm install
npm run dev          # http://localhost:5174
```

需启动后端（`scripts/dev.ps1` 或 `mvn ... spring-boot:run`）。Vite 代理 `/api` → `http://localhost:8088`。

## 构建

```bash
mvn -pl apps/server -am package -DskipTests -Pbundle   # 单 Jar 自动构建 web/dist
```

或 `cd web && npm run build` → `web/dist/`

## 目录

```
web/src/
├── api/            # 接口（含 llm-config.ts）
├── views/          # 页面（settings/llm.vue 大模型配置）
├── stores/         # Pinia（config、auth）
└── router/         # 路由与 adminOnly 守卫
```

## 相关文档

- [docs/api.md](../docs/api.md)
- [docs/产品说明.md](../docs/产品说明.md)
