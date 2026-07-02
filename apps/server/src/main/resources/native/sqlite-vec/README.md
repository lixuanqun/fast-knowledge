# sqlite-vec native 扩展

单机版（`standalone` profile）可将对应平台的 `vec0` 扩展库放到以下目录，应用启动时会自动解压加载：

```
native/sqlite-vec/
├── linux-x86_64/vec0.so
├── macos-aarch64/vec0.dylib
├── macos-x86_64/vec0.dylib
└── windows-x86_64/vec0.dll
```

扩展库可从 [sqlite-vec Releases](https://github.com/asg017/sqlite-vec/releases) 下载。

若未打包扩展库，应用会回退到 **Java 余弦相似度全表扫描**（适合开发与小数据量；生产 Linux 建议放置 `vec0.so`）。

也可通过环境变量显式指定：

```bash
export SQLITE_VEC_EXTENSION=/opt/fast-knowledge/lib/vec0.so
```
