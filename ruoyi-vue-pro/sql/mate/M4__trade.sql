-- M4 交易与会员（对齐芋道 BaseDO）
-- 契约来源：数据库文档-后端乙.md；审计列按甲的 BaseDO 适配（created_at/updated_at → create_time/update_time）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `wallet_account` (
  `account_id`       BIGINT        NOT NULL COMMENT '雪花主键',
  `user_id`          BIGINT        NOT NULL COMMENT '用户ID,一人一户',
  `available_amount` DECIMAL(12,2) NOT NULL DEFAULT '0.00' COMMENT '可用余额镜像',
  `frozen_amount`    DECIMAL(12,2) NOT NULL DEFAULT '0.00' COMMENT '冻结金额镜像',
  `version`          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `creator`          VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开户时间',
  `updater`          VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近账务变动',
  `deleted`          BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='钱包账户表';

CREATE TABLE IF NOT EXISTS `wallet_transfer` (
  `transfer_id` BIGINT        NOT NULL COMMENT '雪花主键',
  `transfer_no` VARCHAR(32)   NOT NULL COMMENT '业务单号,支付幂等键',
  `payer_id`    BIGINT        NOT NULL COMMENT '付款方',
  `payee_id`    BIGINT        NOT NULL COMMENT '收款方',
  `session_id`  BIGINT        NULL COMMENT '来源会话',
  `split_id`    BIGINT        NULL COMMENT '分摊单ID',
  `amount`      DECIMAL(12,2) NOT NULL COMMENT '金额,元',
  `scene`       TINYINT       NOT NULL COMMENT '1直转2分摊3二手',
  `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '0待支付1待到账2已到账3已撤销4已冻结',
  `risk_level`  TINYINT       NOT NULL DEFAULT 0 COMMENT '0低1中2高',
  `delay_until` DATETIME      NULL COMMENT '延迟到账截止',
  `remark`      VARCHAR(100)  NULL COMMENT '附言,敏感词初审',
  `finished_at` DATETIME      NULL COMMENT '终态时间',
  `creator`     VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`transfer_id`),
  UNIQUE KEY `uk_transfer_no` (`transfer_no`),
  KEY `idx_payer_time` (`payer_id`, `create_time`),
  KEY `idx_payee_time` (`payee_id`, `create_time`),
  KEY `idx_split` (`split_id`),
  KEY `idx_status_delay` (`status`, `delay_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='转账单表';

CREATE TABLE IF NOT EXISTS `wallet_flow` (
  `flow_id`            BIGINT        NOT NULL COMMENT '雪花主键',
  `transfer_id`        BIGINT        NOT NULL COMMENT '关联转账单',
  `user_id`            BIGINT        NOT NULL COMMENT '流水归属用户',
  `direction`          TINYINT       NOT NULL COMMENT '1转出2转入',
  `amount`             DECIMAL(12,2) NOT NULL COMMENT '变动金额',
  `balance_after`      DECIMAL(12,2) NOT NULL COMMENT '记账后余额,对账复算',
  `institution_serial` VARCHAR(64)   NULL COMMENT '支付机构流水号',
  `creator`            VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记账时间,只追加',
  `updater`            VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`flow_id`),
  KEY `idx_transfer` (`transfer_id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资金流水表';

CREATE TABLE IF NOT EXISTS `trade_split_order` (
  `split_id`     BIGINT        NOT NULL COMMENT '雪花主键',
  `split_no`     VARCHAR(32)   NOT NULL COMMENT '分摊单号,幂等键',
  `demand_id`    BIGINT        NOT NULL COMMENT '关联需求单',
  `initiator_id` BIGINT        NOT NULL COMMENT '发起人',
  `total_amount` DECIMAL(12,2) NULL COMMENT '活动结束后填总额',
  `member_count` SMALLINT      NOT NULL COMMENT '参与人数',
  `per_amount`   DECIMAL(12,2) NULL COMMENT '人均金额',
  `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '0草稿1待确认2收集中3已完成4已取消',
  `creator`      VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '成团时建草稿',
  `updater`      VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`split_id`),
  UNIQUE KEY `uk_split_no` (`split_no`),
  KEY `idx_demand` (`demand_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分摊单表';

CREATE TABLE IF NOT EXISTS `member_order` (
  `order_id`       BIGINT        NOT NULL COMMENT '雪花主键',
  `order_no`       VARCHAR(32)   NOT NULL COMMENT '商户订单号,回调幂等键',
  `user_id`        BIGINT        NOT NULL COMMENT '订购用户',
  `order_type`     TINYINT       NOT NULL DEFAULT 1 COMMENT '1会员2增值',
  `package_id`     INT           NOT NULL COMMENT '套餐编号',
  `duration_month` SMALLINT      NOT NULL COMMENT '订购时长,月',
  `amount`         DECIMAL(12,2) NOT NULL COMMENT '应付金额',
  `paid_amount`    DECIMAL(12,2) NULL COMMENT '实付金额',
  `pay_channel`    VARCHAR(16)   NULL COMMENT '支付渠道(MOCK)',
  `pay_no`         VARCHAR(64)   NULL COMMENT '支付流水号',
  `status`         TINYINT       NOT NULL DEFAULT 0 COMMENT '0待支付1支付中2已生效3已退款4已关闭5已失效',
  `expire_at`      DATETIME      NULL COMMENT '权益到期时间',
  `paid_at`        DATETIME      NULL COMMENT '支付完成时间',
  `creator`        VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会员/增值订单表';

CREATE TABLE IF NOT EXISTS `member_benefit` (
  `benefit_id`      BIGINT      NOT NULL COMMENT '雪花主键',
  `user_id`         BIGINT      NOT NULL COMMENT '用户ID',
  `benefit_key`     VARCHAR(48) NOT NULL COMMENT '权益标识,如publish_daily_limit',
  `benefit_value`   VARCHAR(64) NOT NULL COMMENT '权益值',
  `source_order_no` VARCHAR(32) NOT NULL COMMENT '发放来源单号,幂等键',
  `expire_at`       DATETIME    NULL COMMENT '失效时间',
  `status`          TINYINT     NOT NULL DEFAULT 0 COMMENT '0生效1失效2回收',
  `creator`         VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发放时间',
  `updater`         VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         BIT(1)      NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`benefit_id`),
  UNIQUE KEY `uk_user_key_order` (`user_id`, `benefit_key`, `source_order_no`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='权益记录表';

CREATE TABLE IF NOT EXISTS `trade_pay_notify` (
  `id`          BIGINT        NOT NULL COMMENT '雪花主键',
  `event_id`    VARCHAR(64)   NOT NULL COMMENT '机构事件唯一标识,去重键',
  `order_no`    VARCHAR(32)   NOT NULL COMMENT '商户订单号',
  `trade_no`    VARCHAR(64)   NOT NULL COMMENT '机构流水号',
  `amount`      DECIMAL(12,2) NOT NULL COMMENT '回调金额',
  `status`      VARCHAR(16)   NOT NULL COMMENT 'SUCCESS/CLOSED/FAILED',
  `raw_body`    VARCHAR(1000) NULL COMMENT '原始报文',
  `handled_at`  DATETIME      NULL COMMENT '处理时间',
  `creator`     VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  `updater`     VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_order` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付回调记录表';

CREATE TABLE IF NOT EXISTS `recon_diff` (
  `id`             BIGINT        NOT NULL COMMENT '雪花主键',
  `diff_no`        VARCHAR(32)   NOT NULL COMMENT '差错单号',
  `biz_no`         VARCHAR(32)   NOT NULL COMMENT '业务单号',
  `local_amount`   DECIMAL(12,2) NULL COMMENT '本地金额',
  `channel_amount` DECIMAL(12,2) NULL COMMENT '渠道金额',
  `type`           TINYINT       NOT NULL COMMENT '1金额2状态3单边',
  `status`         TINYINT       NOT NULL DEFAULT 0 COMMENT '0待处理1已处理',
  `creator`        VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
  `updater`        VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_diff_no` (`diff_no`),
  KEY `idx_biz` (`biz_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='对账差错表';
