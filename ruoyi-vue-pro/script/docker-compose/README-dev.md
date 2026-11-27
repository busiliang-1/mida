# 本地开发环境（MySQL + Redis）

> 只起数据库和缓存，后端用 IDE 或 `mvn spring-boot:run` 直接跑。
> 全栈部署（含 jar 打包、Nginx）请用同目录的 `docker-compose.yml`。

## 快速开始

```bash
cd ruoyi-vue-pro/script/docker-compose

# 启动
docker compose -f docker-compose-dev.yml up -d

# 查看状态（两个都要 healthy）
docker compose -f docker-compose-dev.yml ps

# 停止（保留数据）
docker compose -f docker-compose-dev.yml down

# 停止并清空数据（下次启动重新初始化表结构）
docker compose -f docker-compose-dev.yml down -v
```

## 连接信息

| 项 | 值 |
| --- | --- |
| MySQL | `127.0.0.1:3307`，用户 `root`，**空密码** |
| 库名 | `ruoyi-vue-pro` |
| Redis | `127.0.0.1:6379`，无密码 |
| 表数 | 91 张（底座 59 + 业务 32） |

> ⚠️ MySQL 用 **3307** 不是 3306，避免与本机已有实例冲突。
> 要改回 3306：`MYSQL_PORT=3306 docker compose -f docker-compose-dev.yml up -d`

## 初始化了什么

首次启动（数据卷为空）时按文件名顺序自动执行：

```
01-ruoyi-vue-pro.sql   底座表（59 张）
02-quartz.sql         定时任务表
03-M1__auth.sql       sys_user / auth_record
04-M2__match.sql      mate_demand / mate_signup / mate_like / mate_match
05-M3__im.sql         im_session / im_message / im_session_member / im_blacklist / notify_record
06-M4__trade.sql      wallet_* / trade_split_order / member_* / recon_diff
07-M5__moment.sql     moment_* / user_follow / homepage_config
08-M6__risk.sql       risk_report / risk_event / risk_blacklist
09-M7__goods.sql      goods_item / goods_order / goods_category
```

**验证表是否建全：**

```bash
docker exec mida-dev-mysql-1 mysql -u root -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro';"
# 期望 91
```

## 后端怎么连

`yudao-server/src/main/resources/application-local.yaml`（已 gitignore，需自己建）：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          url: jdbc:mysql://127.0.0.1:3307/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true
          username: root
          password: ''
  data:
    redis:
      host: 127.0.0.1
      port: 6379

yudao:
  mate:
    internal-token: dev-internal-token   # 内部接口密钥，为空则一律拒绝
    pay:
      mock-secret: dev-pay-secret         # 支付回调验签，为空则回调一律 5001
```

**启动：**

```bash
cd ruoyi-vue-pro
mvn -pl yudao-module-mate -am -DskipTests install     # 首次
cd yudao-server
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**验证服务：**

```bash
curl http://127.0.0.1:48080/actuator/health
curl http://127.0.0.1:48080/v3/api-docs -o contract/openapi.yaml   # 导出字段级契约
```

接口文档：<http://127.0.0.1:48080/doc.html>

## 常见问题

| 现象 | 原因 / 处理 |
| --- | --- |
| `failed to resolve reference ... registry-1.docker.io` | 镜像源不通。已在 `~/.docker/daemon.json` 配了 `registry-mirrors`，改完需重启 Docker Desktop |
| `mysql: healthy` 但表是空的 | 初始化只跑一次。`down -v` 清空数据卷后重启 |
| 端口 3307 被占用 | 换端口：`MYSQL_PORT=3308 docker compose ... up -d`，同步改 yaml |
| 后端报 `Access denied` | 确认 `password: ''`（空字符串，不是省略该行） |
| Redis 连接失败 | 本环境 Redis **无密码**，yaml 里不要配 `password` |

## 为什么单独一份 compose

`docker-compose.yml` 是全栈部署版：需要先 `mvn package` 生成 `yudao-server.jar`，
还要前端 `web/dist` 产物，才能起 `yudao-server` 和 `web` 两个服务。
开发调试阶段用不上那套，只起依赖更轻、启动更快。