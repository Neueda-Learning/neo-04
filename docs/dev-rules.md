# neo-04 开发规范

## Liquibase — 数据库变更规则

### 核心原则：已执行的 changeset 永远不能修改

一个 changeset 文件一旦在任何环境（dev、prod、本地）执行过，它就被"冻结"了。
Liquibase 在执行时会记录 checksum，之后每次启动都校验。**修改了就崩溃。**

这就是 PR#21 的根本原因：`005` 在 dev 上执行后被直接编辑，
导致 checksum 不匹配，ECS 容器无法启动，每次部署都失败。

---

### 正确做法：永远只添加新文件

❌ **禁止**
```
# 直接编辑已存在的 changeset 文件
005-seed-watchlist-entries-v1.sql  ← 绝对不能改
```

✅ **正确**
```
# 新建下一个编号的文件
009-your-description.sql
```

然后在 `db.changelog-master.yaml` 末尾追加：
```yaml
  - include:
      file: db/changelog/changes/009-your-description.sql
```

---

### 当前 changeset 文件（已应用，冻结，禁止修改）

| 文件 | 内容 | 状态 |
|------|------|------|
| `001-create-demo-showcase.yaml` | 初始 demo 表 | 🔒 冻结 |
| `002-create-screening-case.yaml` | 核心 schema | 🔒 冻结 |
| `003-drop-demo-showcase.yaml` | 删除 demo 表 | 🔒 冻结 |
| `004-seed-high-risk-countries-v1.sql` | 高风险国家种子数据 | 🔒 冻结 |
| `005-seed-watchlist-entries-v1.sql` | watchlist 100 条种子数据 | 🔒 冻结 |
| `006-alter-screening-config-current-version.yaml` | 修改 screening_config | 🔒 冻结 |
| `007-correct-watchlist-seed-names.sql` | 更新 4 条 watchlist 名字 | 🔒 冻结 |
| `008-fix-watchlist-dob-and-trailing-space.sql` | 修 WL-001 空格 + WL-002 DOB | 🔒 冻结 |

下一个新文件命名：**`009-...`**

---

### 禁止的操作

1. **不能编辑任何已存在的 changeset 文件**（无论内容多小的改动）
2. **不能重命名 changeset 文件**（文件名是 identity 的一部分）
3. **不能删除 changeset 文件**（缺少文件同样会报错）
4. **不能使用 `clear-checksums`** 作为修复手段
   - `spring.liquibase.clear-checksums=true` 或 `LIQUIBASE_CLEAR_CHECKSUMS=true`
   - 后果：app 能启动，但修改的 SQL **永远不会重新执行**，数据库保持旧状态

---

### 配置文件规范

#### 禁止修改（配置已被 prod 和 CI 固定）

| 文件 | 原因 |
|------|------|
| `backend/src/main/resources/application.yml` | 生产配置，CI/CD 读取 |
| `infra/env/dev.params` | AWS dev 环境参数 |
| `infra/env/prod.params` | AWS prod 环境参数 |
| `infra/service.yaml` | CloudFormation 模板 |
| `.github/workflows/pipeline.yml` | CI/CD pipeline |

#### 不需要的文件

- `backend/src/main/resources/application-dev.yml`：已删除，不要重建
  - 它存在的唯一目的是 `clear-checksums`，这是错的

---

### 本地修复数据库（changeset 冲突时）

如果本地出现 checksum 错误：

```bash
# 重置本地数据库（本地数据可以丢弃）
./scripts/reset-db.sh

# 或者完全重建
docker compose down -v
docker compose up --build
```

**不要**去修 changeset 文件。

---

### 快速参考

```
需要改数据？ → 新建 00N-描述.sql
需要改结构？ → 新建 00N-描述.yaml
改完加到   → db.changelog-master.yaml 末尾
本地冲突？  → scripts/reset-db.sh
```
