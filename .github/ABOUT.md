# GitHub 仓库展示说明

维护者可通过脚本同步仓库 **About** 描述与 **Topics**，使 GitHub 首页对开发者清晰展示定位与特性。

## 一键同步

```powershell
.\scripts\set-github-metadata.ps1
```

需已安装并登录 [GitHub CLI](https://cli.github.com/)（`gh auth login`）。

## 仓库描述（Description）

来源：[description.txt](./description.txt)

> 面向中小企业的开源私有化知识库：单实例部署、数据本地可控、LLM 中立。LangChain4j RAG、PgVector 混合检索、Docker 一键部署。

## 推荐 Topics

`knowledge-base` · `rag` · `langchain4j` · `pgvector` · `self-hosted` · `private-deployment` · `llm-agnostic` · `spring-boot` · `vue3` · `docker` · `open-source` 等（GitHub 上限 20 个）

完整列表见 [set-github-metadata.ps1](../scripts/set-github-metadata.ps1)。

## 开发者首屏阅读顺序

1. [README.md](../README.md) — 定位、特性清单、快速启动
2. [docs/产品说明.md](../docs/产品说明.md) — 完整功能与场景
3. [docs/fast_knowledge.md](../docs/fast_knowledge.md) — 技术架构
4. [docs/api.md](../docs/api.md) — 接口契约
