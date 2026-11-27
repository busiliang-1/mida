# contract/ — 契约 v1

> **冻结时间：** 2026-11-27 ｜ **基线提交：** `230b018` ｜ **维护：** 测试兼文档
>
> 本目录是**接口、错误码、事件的唯一真相**。与规划文档冲突时，**以本目录为准**。

## 目录

| 文件 | 内容 | 谁来用 |
| --- | --- | --- |
| `openapi.yaml` | 74 个接口，自动生成 | 前端生成 TS 类型 / Apifox 导入 |
| `error-codes.md` | 12 个全局码 + 域内细分码 + `2001` 真实语义 | 前端错误处理 / 测试写断言 |
| `events.md` | 8→5 事件实况 + 联调核对表 | 联调 / 测试 |
| `差异对照表.md` | 文档 vs 代码的 8 处偏差 | 评审会 / 所有人 |

## 三条铁律

1. **改契约先改本目录**，再改代码，最后走评审。顺序反了 PR 打回。
2. **禁止手写 DTO**。前端用 `openapi-typescript` 生成。
3. **CI 会校验契约漂移**：源码注解改了但 `openapi.yaml` 没重新生成 → 红。

## 前端怎么用

```bash
# 1. 生成 TS 类型
npx openapi-typescript contract/openapi.yaml -o src/api/generated/types.ts

# 2. 三个 baseURL（前缀不统一，见差异对照表 D1）
const BASE_APP   = 'http://127.0.0.1:48080/app-api/mate'    # M1 认证 / M2 匹配 / M6 风控
const BASE_ADMIN = 'http://127.0.0.1:48080/admin-api/mate'  # 后台审核
const BASE_V1    = 'http://127.0.0.1:48080/api/v1'          # M3 IM / M4 交易 / M5 动态 / M7 二手
```

## 关键约定速查

| 项 | 规则 |
| --- | --- |
| 响应结构 | `{code, message, traceId, data}`，`code=0` 才算成功 |
| Long ID | 后端已序列化为 **String**，前端禁止 `parseInt` |
| 金额 | 字符串传输，前端用 `decimal.js`，禁止浮点运算 |
| 分页 | `cursor`+`size` → `nextCursor`+`hasMore`，**无总条数** |
| 幂等 | 写接口必带 `Idempotency-Key`（≤32 字符），**重试沿用同一个值** |
| 内部接口 | `/internal/v1/**` 需 `X-Internal-Token`，前端**永远不要调** |
| 敏感词 | 动态/评论不报错转人工；IM/转账/二手**直接 4001** |

## 转账限额（以代码为准）

| 参数 | 值 |
| --- | --- |
| 单笔上限 | **2000.00** |
| 日累计上限 | **5000.00** |
| 延迟到账 | **24 小时** |

> ⚠️ 规划文档写的是 200/500/2h，**代码是 2000/5000/24h**。以代码为准，待评审会确认。

## 重新生成 openapi.yaml

```bash
python scripts/gen_contract.py
```

脚本直接读控制器源码注解，**方法级 `@RequireAuthLevel` 优先于类级**。改了接口必须重跑，否则 CI 红。

## 待办（服务起来后补全）

- [ ] 用 `curl http://127.0.0.1:48080/v3/api-docs` 导出**字段级**定义（当前 openapi.yaml 只有路由与鉴权，无 request/response 字段）
- [ ] 补 `contract/bridge.md`（Capacitor 桥接约定，前端乙负责）
- [ ] 评审会拍板后打 tag：`git tag contract-v1`

## 变更记录

| 日期 | 版本 | 变更 |
| --- | --- | --- |
| 2026-11-27 | v1.0-draft | 从源码反向生成，修正 8 处文档偏差 |