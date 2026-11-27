-- M3 即时通讯（对齐芋道 BaseDO：creator/create_time/updater/update_time/deleted）
-- 契约来源：数据库文档-后端乙.md（业务字段/唯一约束按文档，审计列按甲的 BaseDO 适配）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `im_session` (
  `session_id`   BIGINT      NOT NULL COMMENT '雪花主键',
  `type`         TINYINT     NOT NULL COMMENT '1单聊2群聊',
  `demand_id`    BIGINT      NULL COMMENT '群聊关联需求单',
  `match_id`     BIGINT      NULL COMMENT '单聊关联匹配',
  `member_count` SMALLINT    NOT NULL DEFAULT 0 COMMENT '群成员数',
  `max_sequence` BIGINT      NOT NULL DEFAULT 0 COMMENT '当前最大序列号',
  `status`       TINYINT     NOT NULL DEFAULT 0 COMMENT '0正常1封禁2归档',
  `creator`      VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`      VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近活跃时间',
  `deleted`      BIT(1)      NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`session_id`),
  KEY `idx_demand` (`demand_id`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会话表';

CREATE TABLE IF NOT EXISTS `im_message` (
  `message_id`    BIGINT        NOT NULL COMMENT '雪花主键',
  `session_id`    BIGINT        NOT NULL COMMENT '会话ID',
  `seq`           BIGINT        NOT NULL COMMENT '会话内单调递增序列号',
  `sender_id`     BIGINT        NOT NULL COMMENT '发送者ID',
  `content_type`  TINYINT       NOT NULL COMMENT '1文本2图片3卡片',
  `content`       VARCHAR(1000) NULL COMMENT '文本内容',
  `card_type`     TINYINT       NULL COMMENT '1需求2转账3二手',
  `biz_no`        VARCHAR(32)   NULL COMMENT '卡片单据编号,只带编号',
  `client_msg_id` VARCHAR(64)   NOT NULL COMMENT '客户端去重键',
  `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '0发送中1已送达2失败3撤回',
  `creator`       VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `updater`       VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`message_id`),
  UNIQUE KEY `uk_session_seq` (`session_id`, `seq`),
  UNIQUE KEY `uk_session_client` (`session_id`, `client_msg_id`),
  KEY `idx_session_time` (`session_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息表';

CREATE TABLE IF NOT EXISTS `im_session_member` (
  `id`            BIGINT      NOT NULL COMMENT '雪花主键',
  `session_id`    BIGINT      NOT NULL COMMENT '会话ID',
  `user_id`       BIGINT      NOT NULL COMMENT '成员ID',
  `role`          TINYINT     NOT NULL DEFAULT 0 COMMENT '0成员1群主',
  `last_read_seq` BIGINT      NOT NULL DEFAULT 0 COMMENT '已读最大序列号',
  `creator`       VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `updater`       VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       BIT(1)      NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_user` (`session_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会话成员表';

CREATE TABLE IF NOT EXISTS `im_blacklist` (
  `id`              BIGINT      NOT NULL COMMENT '雪花主键',
  `user_id`         BIGINT      NOT NULL COMMENT '拉黑发起者',
  `blocked_user_id` BIGINT      NOT NULL COMMENT '被拉黑者',
  `creator`         VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
  `updater`         VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         BIT(1)      NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_blocked` (`user_id`, `blocked_user_id`),
  KEY `idx_blocked` (`blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='拉黑表';

CREATE TABLE IF NOT EXISTS `notify_record` (
  `id`          BIGINT       NOT NULL COMMENT '雪花主键',
  `user_id`     BIGINT       NOT NULL COMMENT '接收者',
  `level`       TINYINT      NOT NULL COMMENT '1即时2聚合3定时',
  `biz_type`    VARCHAR(32)  NOT NULL COMMENT '业务类型',
  `payload`     VARCHAR(500) NULL COMMENT '推送内容',
  `sent_at`     DATETIME     NULL COMMENT '下发时间',
  `read_flag`   TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读1已读',
  `creator`     VARCHAR(64)  NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     VARCHAR(64)  NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_sent` (`user_id`, `sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='推送记录表';
