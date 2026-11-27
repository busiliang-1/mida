# contract/ — 契约 v1

> **冻结时间：** 2026-11-27 ｜ **基线提交：** `230b018` ｜ **维护：** 测试兼文档
>
> 本目录是**接口、错误码、事件的唯一真相**。与规划文档冲突时，**以本目录为准**。

## 目录

| 文件 | 内容 | 谁来用 |
| --- | --- | --- |
| `openapi.full.json` | **74 接口 + 138 schema 的完整字段定义**，运行时导出 | **前端生成 TS 类型首选这个** |
| `openapi.yaml` | 74 接口的路由/鉴权/幂等标记，源码生成 | CI 漂移检测 |
| `error-codes.md` | 12 个全局码 + 域内细分码 + `2001` 真实语义 | 前端错误处理 / 测试写断言 |
| `events.md` | 8→5 事件实况 + 联调核对表 | 联调 / 测试 |
| `差异对照表.md` | 文档 vs 代码的 8 处偏差 | 评审会 / 所有人 |

> **两个 openapi 文件的关系**：`.full.json` 有字段、可以生类型；`.yaml` 只有路由、可以离线跑。
> 两者接口数量已校验一致（66 path / 74 operation）。改接口时**两个都要更新**。

## 三条铁律

1. **改契约先改本目录**，再改代码，最后走评审。顺序反了 PR 打回。
2. **禁止手写 DTO**。前端用 `openapi-typescript` 生成。
3. **CI 会校验契约漂移**：源码注解改了但 `openapi.yaml` 没重新生成 → 红。

## 前端怎么用

```bash
# 1. 生成 TS 类型（用带字段的完整版）
npx openapi-typescript contract/openapi.full.json -o src/api/generated/types.ts

# 2. 三个 baseURL（前缀不统一，见差异对照表 D1）
const BASE_APP   = 'http://127.0.0.1:48080/app-api/mate'    # M1 认证 / M2 匹配 / M6 风控
const BASE_ADMIN = 'http://127.0.0.1:48080/admin-api/mate'  # 后台审核
const BASE_V1    = 'http://127.0.0.1:48080/api/v1'          # M3 IM / M4 交易 / M5 动态 / M7 二手
```

## 关键约定速查

| 项 | 规则 |
| --- | --- |
| 响应结构 | `{code, message, traceId, data}`，`code=0` 才算成功 |
| **Long 序列化** | **条件式**：超出 ±2^53-1 → String；范围内 → Number（见下） |
| 金额 | `decimal(12,2)`，前端用 `decimal.js`，禁止浮点运算 |
| 分页 | `cursor`+`size` → `nextCursor`+`hasMore`，**无总条数** |
| 幂等 | 写接口必带 `Idempotency-Key`（≤32 字符），**重试沿用同一个值** |
| 内部接口 | `/internal/v1/**` 需 `X-Internal-Token`，前端**永远不要调** |
| 敏感词 | 动态/评论不报错转人工；IM/转账/二手**直接 4001** |
| **请求编码** | 请求头必须 `Content-Type: application/json; charset=utf-8`，否则中文变 `????` |

### Long 序列化的真实行为（实测）

底座 `NumberSerializer` 是**条件式**的，不是无条件转字符串：

```java
if (longValue > -2^53+1 && longValue < 2^53-1)  →  输出 Number
else                                            →  输出 String
```

**实测结果：**

```json
// 注册：雪花 ID 超大 → 字符串 ✅
{"code":0,"msg":"","data":"2126301494723645441"}

// profile：等级是小数字 → 仍是 Number
{"userId":"2126301494723645441","authLevel":1,"creditScore":600}
```

**前端该怎么处理：**

- ❌ 不要写 `typeof id === 'string'` 做类型判断
- ❌ 不要 `Number(id)` 转换雪花 ID（会丢精度）
- ✅ **凡是以 `Id` / `No` 结尾的字段（userId、demandId、transferNo…）一律按 `string` 处理**
- ✅ 等级、状态、计数等小整数按 `number` 处理
- ✅ `openapi.full.json` 里 schema 类型是 `int64`，生成 TS 后需手动改成 `string`

**验证方法：** 前端联调时，对任何 ID 字段打印 `typeof`，确认是 `string`。

### 中文编码注意事项（测试必看）

实测过程中出现过昵称变 `????`（数据库 `HEX` = `3F3F3F3F`），排查后确认是**请求端编码问题**，不是系统 Bug：

| 场景 | 结果 |
| --- | --- |
| 用 PowerShell `ConvertTo-Json` 直接发（默认非 UTF-8） | 入库变成 `????` |
| 显式 UTF-8 编码 + `charset=utf-8` | 正常，`HEX` = `E5BCA0E5908CE5ADA6`（"张同学"） |

**测试同学注意：** 用 Apifox / curl 时确认请求体是 UTF-8。写用例时不要因为工具编码问题误报「中文乱码 Bug」。

## 转账限额（以代码为准）

| 参数 | 值 |
| --- | --- |
| 单笔上限 | **2000.00** |
| 日累计上限 | **5000.00** |
| 延迟到账 | **24 小时** |

> ⚠️ 规划文档写的是 200/500/2h，**代码是 2000/5000/24h**。以代码为准，待评审会确认。

## 重新生成契约

```bash
# 1. 路由级（离线可跑，CI 漂移检测用）
python scripts/gen_contract.py

# 2. 字段级（需先起服务）
python scripts/ci/export_runtime_spec.py
```

`gen_contract.py` 直接读控制器源码注解，**方法级 `@RequireAuthLevel` 优先于类级**。
改了接口必须重跑两个脚本，否则 CI 会红。

## 环境已就绪（2026-11-27 实测）

| 项 | 状态 |
| --- | --- |
| MySQL | `127.0.0.1:3307`，root 空密码，91 张表（含 32 张业务表） |
| Redis | `127.0.0.1:6379`，无密码 |
| 后端 | `http://127.0.0.1:48080`，local profile，启动约 21 秒 |
| Swagger | `http://127.0.0.1:48080/doc.html` |
| 实测链路 | 发验证码 → 注册 → 登录 → 查 profile，全部 `code=0` |

起环境见 `ruoyi-vue-pro/script/docker-compose/README-dev.md`。

> 注：`/actuator/health` 在本项目返回 404，用 `doc.html` 或任一业务接口判断服务是否就绪。

## 待办

- [x] 导出字段级契约 `openapi.full.json`
- [ ] 补 `contract/bridge.md`（Capacitor 桥接约定，前端乙负责）
- [ ] 评审会拍板后打 tag：`git tag contract-v1`
- [ ] 前端联调时确认所有 ID 字段的 `typeof` 为 `string`

## 变更记录

| 日期 | 版本 | 变更 |
| --- | --- | --- |
| 2026-11-27 | v1.0-draft | 从源码反向生成，修正 8 处文档偏差 |
| 2026-11-27 | v1.1-draft | 服务跑通，补字段级契约；实测补 Long 条件序列化与中文编码注意事项 |