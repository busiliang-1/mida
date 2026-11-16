-- M6 风控与内容安全（对齐芋道 BaseDO）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `risk_report` (
  `report_id`    BIGINT       NOT NULL,
  `reporter_id`  BIGINT       NOT NULL COMMENT '举报人',
  `target_type`  TINYINT      NOT NULL COMMENT '1用户2动态3会话4转账5二手',
  `target_id`    BIGINT       NOT NULL COMMENT '被举报对象',
  `reason`       TINYINT      NOT NULL COMMENT '举报理由',
  `evidence_ref` VARCHAR(255) NULL COMMENT '证据对象键',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待受理1处理中2已处置3已驳回',
  `deadline_at`  DATETIME     NOT NULL COMMENT '处理时限,超时升级',
  `creator`      VARCHAR(64)  NULL DEFAULT '',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`      VARCHAR(64)  NULL DEFAULT '',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      BIT(1)       NOT NULL DEFAULT b'0',
  PRIMARY KEY (`report_id`),
  KEY `idx_reporter` (`reporter_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='举报工单';

CREATE TABLE IF NOT EXISTS `risk_event` (
  `event_id`     BIGINT      NOT NULL,
  `user_id`      BIGINT      NOT NULL,
  `event_type`   VARCHAR(32) NOT NULL COMMENT '注册/转账/发布/举报',
  `rule_id`      INT         NULL COMMENT '首个命中规则',
  `rule_version` INT         NOT NULL DEFAULT 1,
  `score`        SMALLINT    NOT NULL DEFAULT 0,
  `advice`       TINYINT     NOT NULL COMMENT '0放行1延迟2冻结3转人工',
  `creator`      VARCHAR(64) NULL DEFAULT '',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`      VARCHAR(64) NULL DEFAULT '',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      BIT(1)      NOT NULL DEFAULT b'0',
  PRIMARY KEY (`event_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='风险事件';

CREATE TABLE IF NOT EXISTS `risk_blacklist` (
  `id`         BIGINT       NOT NULL,
  `user_id`    BIGINT       NOT NULL,
  `type`       TINYINT      NOT NULL COMMENT '1设备2IP3账号',
  `reason`     VARCHAR(200) NULL,
  `expire_at`  DATETIME     NULL,
  `creator`    VARCHAR(64)  NULL DEFAULT '',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`    VARCHAR(64)  NULL DEFAULT '',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    BIT(1)       NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='黑名单';
