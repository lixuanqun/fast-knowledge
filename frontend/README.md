# Fast Knowledge — 前端（Web）

Vue 3 + Vite + Element Plus 管理界面。

## 开发

在仓库根目录：

```powershell
npm install          # 首次
npm run dev:frontend # 仅前端，需后端已启动
npm run dev          # 根目录脚本，同时起前后端
```

访问 http://localhost:5174 ，开发时 Vite 将 `/api` 代理到 `http://localhost:8088`。

## 构建

```powershell
npm run build:frontend
# 或根目录：npm run build
```

产物：`dist/`

## 目录说明

```
frontend/
├── index.html
├── package.json
├── vite.config.ts      # 开发代理 /api
├── src/
│   ├── api/            # 接口封装
│   ├── components/
│   ├── layouts/
│   ├── router/
│   ├── stores/
│   ├── utils/          # axios baseURL: /api
│   └── views/
└── dist/               # 构建输出（gitignore）
```

## 环境变量

生产构建默认使用相对路径 `/api`，与 Nginx 或单 Jar 部署一致。若需指定独立 API 域名，可在根目录添加 `.env.production`：

```
VITE_API_BASE=https://api.example.com/api
```

并在 `src/utils/request.ts` 中读取 `import.meta.env.VITE_API_BASE`（当前默认 `/api`，一般无需修改）。
