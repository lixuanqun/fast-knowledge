# 备份与恢复 Runbook

## 备份

```bash
bash scripts/backup/backup-all.sh
# 指定配置与输出目录：
bash scripts/backup/backup-all.sh --env-file /opt/fast-knowledge/.env /backups/fk
```

默认输出到 `/opt/fast-knowledge/backups/YYYYMMDD_HHMMSS/`，包含：

- `fast_knowledge.sql` — MySQL 逻辑备份（原生 mysqldump，单事务一致性）
- `minio/`（mc mirror）或 `minio_data.tar.gz`（本机 MinIO 数据目录）
- `vectors.tar.gz` — 本地向量索引（可由文档重建，可选）

凭据从 `$APP_HOME/.env`（或 `--env-file` 指定文件）读取，保留最近 `KEEP_BACKUPS`（默认 14）份。

定时备份示例（每日 02:00）：

```bash
echo '0 2 * * * root bash /opt/fast-knowledge/backup-all.sh >> /var/log/fk-backup.log 2>&1' \
  > /etc/cron.d/fast-knowledge-backup
```

## 恢复

```bash
bash scripts/backup/restore-all.sh backups/20260703_120000
# 恢复后重启应用：
systemctl restart fast-knowledge
```

## 建议

| 项 | 建议 |
|----|------|
| RPO | 每日备份（生产） |
| RTO | 视数据量，通常 30 分钟内 |
| 验证 | 每季度在测试环境演练恢复 |
