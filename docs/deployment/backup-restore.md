# 备份与恢复 Runbook

## 备份

```bash
bash scripts/backup/backup-all.sh
```

默认输出到 `backups/YYYYMMDD_HHMMSS/`，包含：

- `fast_knowledge.sql` — PostgreSQL 逻辑备份
- `minio_data.tar.gz` — MinIO 卷（若检测到）

## 恢复

```bash
bash scripts/backup/restore-all.sh backups/20260703_120000
```

恢复后重启应用容器。

## 建议

| 项 | 建议 |
|----|------|
| RPO | 每日备份（生产） |
| RTO | 视数据量，通常 30 分钟内 |
| 验证 | 每季度在测试环境演练恢复 |
