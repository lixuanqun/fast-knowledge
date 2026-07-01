# Fast Knowledge — Web 前端

Vue 3 + Vite + Element Plus 管理界面。

## 开发

```powershell
cd web
npm install          # 首次
npm run dev          # http://localhost:5174
```

需另开终端启动服务端：`mvn -pl apps/server spring-boot:run`（仓库根目录），或使用 `scripts/dev.ps1` 同时启动。

开发时 Vite 将 `/api` 代理到 `http://localhost:8088`。

## 构建

```powershell
cd web
npm run build
```

产物：`web/dist/`。生产单 Jar 打包时由 Maven `frontend-maven-plugin` 自动构建并复制到 `apps/server/target/classes/static`：

```bash
mvn -pl apps/server -am package -DskipTests -Pbundle
```

## 目录说明

```
web/
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

生产构建默认使用相对路径 `/api`，与 Nginx 或单 Jar 部署一致。若需指定独立 API 域名，可在 `web/` 添加 `.env.production`：

```
VITE_API_BASE=https://api.example.com/api
```

并在 `src/utils/request.ts` 中读取 `import.meta.env.VITE_API_BASE`（当前默认 `/api`，一般无需修改）。
