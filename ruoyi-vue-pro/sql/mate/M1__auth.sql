-- M1 用户与认证（对齐芋道 BaseDO：create_time/update_time/creator/updater/deleted）
-- C 端用户主表，区别于底座 system_users（后台管理员）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `user_id`      BIGINT       NOT NULL COMMENT '雪花主键',
  `nickname`     VARCHAR(32)  NOT NULL COMMENT '昵称',
  `phone_cipher` VARCHAR(128) NOT NULL COMMENT '手机号AES密文',
  `phone_hash`   CHAR(64)     NOT NULL COMMENT '手机号HMAC-SHA256,等值查询',
  `avatar_url`   VARCHAR(255) NULL COMMENT '头像',
  `gender`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0未知1男2女',
  `school_id`    INT          NULL COMMENT '学校编号,L3后写入',
  `grade`        SMALLINT     NULL COMMENT '年级',
  `auth_level`   TINYINT      NOT NULL DEFAULT 1 COMMENT '认证等级1-3',
  `credit_score` SMALLINT     NOT NULL DEFAULT 600 COMMENT '信用分',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常1限制2封禁',
  `creator`      VARCHAR(64)  NULL DEFAULT '' COMMENT '创建者',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`      VARCHAR(64)  NULL DEFAULT '' COMMENT '更新者',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      BIT(1)       NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_phone_hash` (`phone_hash`),
  KEY `idx_status_level` (`status`,`auth_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='觅搭C端用户表';

CREATE TABLE IF NOT EXISTS `auth_record` (
  `record_id`       BIGINT      NOT NULL COMMENT '雪花主键',
  `user_id`         BIGINT      NOT NULL COMMENT '用户ID',
  `auth_type`       TINYINT     NOT NULL COMMENT '1实名(L2)2学校(L3)',
  `auth_level`      TINYINT     NOT NULL COMMENT '本次对应等级',
  `material_digest` CHAR(64)    NULL COMMENT '材料摘要,不存原文',
  `report_no`       VARCHAR(64) NULL COMMENT '学籍报告编号,全局唯一',
  `verify_channel`  VARCHAR(32) NOT NULL COMMENT '核验渠道(MANUAL人工)',
  `verify_result`   TINYINT     NOT NULL COMMENT '1通过2不通过3待人工',
  `expire_at`       DATETIME    NULL COMMENT '结论有效期',
  `operator`        VARCHAR(32) NULL COMMENT '人工审核操作人',
  `creator`         VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         BIT(1)      NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`record_id`),
  UNIQUE KEY `uk_report_no` (`report_no`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='认证记录表';
