# 制造 / 国企场景交付说明

本目录对应企业版「场景模板」能力（`GET /api/scenarios`），用于 POC 与交付验收。

## 模板一览

| ID | 名称 | 推荐分块 | 文档类型 |
|----|------|----------|----------|
| `manufacturing-policy` | 制度汇编库 | 480 / overlap 64 | POLICY, SAFETY |
| `manufacturing-process` | 工艺知识库 | 512 / overlap 80 | PROCESS, QUALITY |
| `manufacturing-equipment` | 设备维保库 | 448 / overlap 64 | EQUIPMENT, FAQ |

机器可读定义见 classpath：`apps/server/src/main/resources/scenarios/*.json`。

## 交付检查清单（通用）

1. 启用企业版：`SPRING_PROFILES_ACTIVE=enterprise` 或 `KNOWLEDGE_EDITION=enterprise`
2. 按模板创建知识库，上传样例文档并填写文号/生效日期
3. 完成索引 → Wiki 审核发布 → 抽检问答并导出 CSV
4. 如需系统集成：创建限定知识库的 API Key

## 社区版 vs 企业版（与代码一致）

| 能力 | 社区版 | 企业版 |
|------|--------|--------|
| 混合检索 / RAG / Wiki 基础 | ✓ | ✓ |
| 知识生命周期 / RAG 运营面板 | ✓ | ✓ |
| LDAP / OIDC | ✗ | ✓ |
| API Key | ✗ | ✓ |
| 审计 CSV 导出 | ✗ | ✓ |
| 制造场景模板 API | ✗ | ✓ |
| 离线安装包 | 文档指引 | 推荐交付方式 |

无需 License Server：通过配置切换发行版即可。
