# 架构守护基线：controller → dal 违规清单

> 由 `MateArchitectureTest#controllerShouldNotAccessDal` 在 CI 中统计。
> **只许减不许增。** 收敛到 0 后，把该测试里的 try/catch 换成 `rule.check(classes)` 即转为硬门禁。

## 规则

```
controller 层不得直接依赖 dal 层
```

分层约定：`controller → service → dal`。controller 跨层直连 Mapper 会导致：
- 业务规则散落在 controller，无法被单测覆盖（service 层测不到）
- 事务边界错位（controller 上没有 `@Transactional`）
- 同一逻辑在多个 controller 重复实现

## 当前违规：56 处

| # | Controller | 违规形式 | 归属模块 | 责任人 |
| --- | --- | --- | --- | --- |
| 1 | `AdminAuthRecordController` | 注入 `AuthRecordMapper`；返回 `AuthRecordDO` | M1 | 后端甲 |
| 2 | `AdminRiskController` | 注入 `RiskEventMapper`；返回 `RiskReportDO`/`RiskEventDO` | M6 | 后端甲 |
| 3 | `InternalReconController` | 注入 `ReconDiffMapper`；返回 `ReconDiffDO` | M4 | 后端乙 |
| 4 | `AppAuthController` | 返回 `SysUserDO`；调用 `OAuth2AccessTokenDO` 方法 | M1 | 后端甲 |
| 5 | `AppDemandController` | 在 controller 里直接 `MateDemandDO.builder()` 组装 DO | M2 | 后端甲 |
| 6 | `AppMatchController` | 返回 `MateMatchDO` | M2 | 后端甲 |
| 7 | `AppGoodsController` | 返回 `SysUserDO`（卖家名片） | M7 | 后端乙 |

> 说明：`OAuth2AccessTokenDO` 属底座 system 模块，登录必需，已单独豁免（见测试注释）。
> 表中第 4 条的违规主要是 `SysUserDO` 直接外泄。

## 收敛建议（按投入产出排序）

| 优先级 | 动作 | 影响面 | 预计 |
| --- | --- | --- | --- |
| P1 | controller 返回 VO 而非 DO，DTO 转换下沉到 service | 4 个 controller 的返回值，**会改变接口响应结构**，须同步改契约 | 中 |
| P2 | 注入 Mapper 的 controller 改调 service 方法 | 3 个 controller | 小 |
| P3 | `AppDemandController` 的 DO 组装移入 service | 1 个 controller | 小 |

> ⚠️ P1 会改响应结构，**必须与前端确认后再动**，否则前端已按 DO 字段写的页面会崩。

## 为什么本期不硬卡

一期功能优先，这 56 处是已有代码的结构问题，不是我这次加 CI 引入的。
若直接硬门禁，所有 PR 红灯，团队会去关掉检查而不是修代码。
所以采取**基线 + 只减不增**：CI 每次打印违规数，谁引入新违规谁负责，
但存量债务由模块责任人按上表逐步收敛。

## 每次 CI 会输出

```
[ArchUnit][WARN] controller→dal 违规 56 处（存量债务，不阻断）。收敛基线见 yudao-module-mate/ARCH-BASELINE.md
```

看 CI 日志里这个数字，**只应该变小**。