# 觅搭 · 事件表（contract-v1）

> **唯一真相来源：** `yudao-module-mate/.../mate/event/*.java` 与各 Service 的 `publishEvent` 调用。
> 本文档由源码核对得出，**与规划 §5 的 8 事件表不一致，已按实际实现修正**。

## 0. 先说结论：8 个规划事件，实际落地 5 个

| 规划事件 | 实际状态 | 处理方式 |
| --- | --- | --- |
| `demand.formed` | ✅ 已实现 | `DemandFormedEvent` |
| `match.created` | ✅ 已实现 | `MatchCreatedEvent` |
| `order.paid` | ✅ 已实现 | `MemberOrderPaidEvent` |
| `content.submitted` | ✅ 已实现 | `MomentSubmittedEvent` |
| `goods.sold` | ✅ 已实现 | `GoodsSoldEvent` |
| `auth.level.changed` | ❌ **不存在** | 用**实时查库校验**替代 |
| `risk.hit` | ❌ **不存在** | 用**同步 `riskService.evaluate()`** 替代 |
| `transfer.pending` | ❌ **不存在** | 用 **`TransferDelayJob` 扫表**替代 |

**决策：采用方案 A（改文档）**，不补两个事件类。理由：乙侧 5 处等级校验全部是实时查库，风控是同步调用，功能已覆盖；补强事件属"事后异步冻结"场景，一期不需要，工期紧不做。

---

## 1. 已实现的 5 个事件

### `demand.formed` — 需求成团

| 项 | 值 |
| --- | --- |
| Java 类 | `DemandFormedEvent` |
| 生产方 | M2 `DemandServiceImpl`（成团时，见 L136 / L167） |
| 消费方 | M3 `ImSessionEventListener`（建群）、M4 `SplitOrderEventListener`（生成分摊草稿） |
| 幂等键 | `demandId` |
| 触发时机 | 事务提交后 |

**关键断言**：成团只发**一次**事件（并发审核报名时不得重复发）。

### `match.created` — 双向匹配成功

| 项 | 值 |
| --- | --- |
| Java 类 | `MatchCreatedEvent` |
| 生产方 | M2 `MatchServiceImpl`（L138） |
| 消费方 | M3 `ImSessionEventListener`（建单聊会话） |
| 幂等键 | `(userLowId, userHighId)` 归一化后唯一 |
| 触发时机 | 双向喜欢落库后 |

**关键断言**：同一对用户只建**一个**单聊会话，匹配表只有**一行**。

### `order.paid` — 订单支付成功

| 项 | 值 |
| --- | --- |
| Java 类 | `MemberOrderPaidEvent` |
| 生产方 | M4 `MemberOrderServiceImpl`（L178） |
| 消费方 | M4 权益发放、M6 |
| 幂等键 | `orderNo`（叠加 `trade_pay_notify.event_id` 唯一） |
| 触发时机 | 支付回调验签通过且首次处理 |

**关键断言**：同一 `event_id` 重复回调 10 次，权益记录**只 1 组**。

### `content.submitted` — 内容提交审核

| 项 | 值 |
| --- | --- |
| Java 类 | `MomentSubmittedEvent` |
| 生产方 | M5 `MomentPostServiceImpl`（发帖 L132、评论 L228） |
| 消费方 | M6 敏感词初审 |
| 幂等键 | `(postId/commentId, contentType)` |
| 触发时机 | 内容落库后 |

**关键断言**：命中敏感词不报错，转 `audit_status=3`；审核中作者可见、他人不可见。

### `goods.sold` — 二手成交

| 项 | 值 |
| --- | --- |
| Java 类 | `GoodsSoldEvent` |
| 生产方 | M7 `GoodsServiceImpl`（线下确认完成时 L192） |
| 消费方 | M4（仅记信用分/统计） |
| 幂等键 | `orderId` |
| 触发时机 | 线下确认完成后 |

**关键断言**：轻量版**无资金托管**，`goods.sold` **不触发钱包扣款**。

---

## 2. 被替代的 3 个事件（为什么不需要）

### `auth.level.changed` → 实时查库

乙侧 5 处等级校验均为**请求时实时查库**：

| 位置 | 校验方式 |
| --- | --- |
| 发需求 | `@RequireAuthLevel(2)` 切面读 `sys_user.auth_level` |
| 转账 | `TransferServiceImpl.assertLevel()` 服务层再守一道 |
| 二手挂售 | `@RequireAuthLevel(3)` |
| 发帖 | `@RequireAuthLevel(2)` |
| 互动 | `@RequireAuthLevel(2)` |

等级一改，**下一次请求立即生效**，不需要事件推送。

### `risk.hit` → 同步 `evaluate()`

`TransferServiceImpl.create()` 在准入阶段**同步调用** `riskService.evaluate(payerId, scene, amount)`，命中黑名单当场返回 `3003` 或标记冻结。

### `transfer.pending` → `TransferDelayJob` 扫表

延迟到账走定时任务扫描 `wallet_transfer.delay_until <= now()` 入账，功能等价且更简单。

---

## 3. 投递机制现状（如实记录，勿美化）

| 规划要求 | 实际实现 | 影响 |
| --- | --- | --- |
| 本地事务 + `mida_event_outbox` 同提交 | ❌ 未实现 | 进程崩溃时事件丢失 |
| `mida_event_consumed` 消费去重 | ❌ 未实现 | 无跨进程消费去重 |
| 支持重放 / 死信 | ❌ 未实现 | 无法重放补偿 |

**实际用的是 Spring 进程内 `@EventListener`。** 单机单进程跑得通（e2e 已验），但**不具备崩溃恢复与跨进程投递能力**。

> 三张支撑表 `mida_idempotency` / `mida_event_outbox` / `mida_event_consumed` 在设计文档里写了，**实际 SQL 中不存在**，全仓库 0 处引用。已在契约中标注为"未实现"。

---

## 4. 联调核对表（事件 × 生产方 × 消费方）

测试时逐格打勾，用同一 `traceId` 串起来：

| 事件 | 生产方 | 消费方 | 幂等键 | 核对点 |
| --- | --- | --- | --- | --- |
| `demand.formed` | M2 | M3 | demandId | 群成员数 = needCount |
| `demand.formed` | M2 | M4 | demandId | 分摊草稿生成 |
| `match.created` | M2 | M3 | userLow/High | 单聊会话唯一 |
| `order.paid` | M4 | M4 | orderNo | 权益只发一次 |
| `content.submitted` | M5 | M6 | postId | 审核状态流转 |
| `goods.sold` | M7 | M4 | orderId | 不触发扣款 |

## 5. 事件类清单（供 ArchUnit / 测试引用）

```
cn.iocoder.yudao.module.mate.event.DemandFormedEvent
cn.iocoder.yudao.module.mate.event.MatchCreatedEvent
cn.iocoder.yudao.module.mate.event.MemberOrderPaidEvent
cn.iocoder.yudao.module.mate.event.MomentSubmittedEvent
cn.iocoder.yudao.module.mate.event.GoodsSoldEvent
```

**监听器：**
```
cn.iocoder.yudao.module.mate.listener.ImSessionEventListener   (@EventListener × 2)
cn.iocoder.yudao.module.mate.listener.SplitOrderEventListener  (@EventListener × 1)
```