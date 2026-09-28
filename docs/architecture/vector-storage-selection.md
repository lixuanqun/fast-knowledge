# 向量存储与检索选型记录

> **状态**：已决策（2026-09 实施） | **关联**：[图存储选型](graph-storage-selection.md)
>
> **决策**：向量引擎采用**双引擎路由**——默认 **Lucene 进程内 HNSW**（`knowledge.vector.engine=lucene`），
> 保留**内存暴力扫描**（`engine=memory`，JSON 落盘）作为小规模/测试形态。两引擎实现同一
> langchain4j `EmbeddingStore` 接口，上层零改动。

---

## 1. 决策背景

原实现为 `LocalEmbeddingStore`（langchain4j `InMemoryEmbeddingStore` + per-KB JSON 持久化），
全量内存 + 暴力余弦扫描。对"万级 chunk"正确且零调优，但存在两处天花板：

1. 向量常驻堆内存（1024 维 ≈ 4KB/向量），10 万 chunk ≈ 400MB，与 JGraphT 子图、Caffeine 共享堆；
2. O(N) 线性扫描与 JSON 全量重写落盘，单 KB 数十万 chunk 后延迟与 flush 成本不可控。

结合三路检索（向量 + FULLTEXT 关键词 + 图 PPR）与垂直场景（检索/问答/生成）的约束：
零新增中间件、纯 Java、离线可达、per-KB 隔离 + docType 元数据过滤。

## 2. 候选对比（2026）

| 候选 | 形态 | 许可 | 结论 |
|---|---|---|---|
| **Lucene HNSW**（已选） | 进程内，段文件落盘 | Apache 2.0 | 过滤检索（BitSet 预过滤）+ int8 量化生态最稳；未来可统一承载全文路 |
| JVector | 进程内，盘上索引 | Apache 2.0 | 内存效率最强（DiskANN），但无全文能力、过滤弱于 Lucene |
| LanceDB | 嵌入式（Rust） | Apache 2.0 | 嵌入式赛道 star 领跑（~11k），但 Java 绑定不成熟 |
| MariaDB 11.8 Vector | 独立服务（换库） | GPLv2 | 需 MySQL→MariaDB 迁移手术，不作为默认 |
| Qdrant | 独立单二进制 | Apache 2.0 | 标准模式多实例/超大规模的后续选项（官方 langchain4j 模块） |
| MySQL 9 CE VECTOR | 独立服务 | — | 社区版只能存不能查（ANN 锁在 HeatWave），排除 |

LightRAG（38.9k★）默认向量栈 NanoVectorDB 即"JSON 暴力扫描"，与本项目的 memory 引擎同型；
其生产建议（统一关系库/专业引擎）印证了"默认轻量、按信号升级"的路线。

## 3. 实施要点

- **分数语义不变**：Lucene COSINE 与 langchain4j 一致为 (1+cos)/2 ∈ [0,1]，
  混合检索加权融合（`HybridFusion`）、UI 相关度、WP4 语义缓存阈值、WP9 缺口采集全部无需调整；
- **过滤翻译**：langchain4j Filter 的 IsEqualTo/And/Or/Not → Lucene Query（keyword 字段），
  HNSW 预过滤；KB 隔离与 docType 过滤下沉到检索层；
- **落盘**：3 秒延迟 commit + 关闭/逐出强制提交（与 memory 引擎同节奏）；
  文档删除走 Lucene 段删除，规避 LightRAG NanoVectorDB O(n²) 删除问题；
- **破坏式变更（无存量迁移）**：默认引擎从内存 JSON 换为 Lucene 段文件，
  旧 `data/vectors/kb-{id}.json` 不读取、不迁移、不兼容；升级后存量知识库的向量索引为空，
  需在管理端执行"重建索引"重灌（Lucene 索引位于 `data/vectors/lucene/kb-{id}/`）。

## 4. 切换信号（出现任一条重评）

1. 单 KB chunk > 50 万或向量堆内存压力不可接受 → 评估 Qdrant（标准模式）或 Lucene 量化参数；
2. 需要更细粒度中文分词 → 将 FULLTEXT 关键词路一并迁入 Lucene（BM25 + 分析器），MySQL FULLTEXT 退役；
3. 多实例横向扩展 → Qdrant/Redis 独立引擎（标准模式）。

## 5. 不引入的东西

- 独立向量数据库（未触发 §4 信号前）
- JNI 系原生库（Faiss/HNSWlib/sqlite-vec）
- Python 生态引擎（Chroma/Milvus Lite）
