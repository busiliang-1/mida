-- M7 二手交易（对齐芋道 BaseDO）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `goods_item` (
  `goods_id`    BIGINT        NOT NULL COMMENT '雪花主键',
  `seller_id`   BIGINT        NOT NULL COMMENT '卖家ID,需L3',
  `title`       VARCHAR(60)   NOT NULL COMMENT '标题,敏感词初审',
  `category`    VARCHAR(16)   NOT NULL COMMENT '书籍/数码/衣物/其他',
  `price`       DECIMAL(12,2) NOT NULL COMMENT '价格',
  `description` VARCHAR(1000) NULL COMMENT '描述',
  `images`      JSON          NOT NULL COMMENT '图片object_key列表',
  `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '0在售1已锁定2已完成3已下架',
  `version`     INT           NOT NULL DEFAULT 0 COMMENT '乐观锁,防重复下单',
  `creator`     VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '挂售时间',
  `updater`     VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`goods_id`),
  KEY `idx_seller` (`seller_id`),
  KEY `idx_category_status` (`category`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='二手商品表';

CREATE TABLE IF NOT EXISTS `goods_order` (
  `order_id`    BIGINT        NOT NULL COMMENT '雪花主键',
  `order_no`    VARCHAR(32)   NOT NULL COMMENT '订单号,幂等键',
  `goods_id`    BIGINT        NOT NULL COMMENT '商品ID',
  `buyer_id`    BIGINT        NOT NULL COMMENT '买家,需L2',
  `seller_id`   BIGINT        NOT NULL COMMENT '卖家',
  `amount`      DECIMAL(12,2) NOT NULL COMMENT '成交价',
  `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '0待确认1已完成2已取消',
  `finished_at` DATETIME      NULL COMMENT '完成时间',
  `creator`     VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `updater`     VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_goods` (`goods_id`),
  KEY `idx_buyer` (`buyer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='二手订单表';

CREATE TABLE IF NOT EXISTS `goods_category` (
  `category_id` INT         NOT NULL COMMENT '分类ID',
  `name`        VARCHAR(16) NOT NULL COMMENT '分类名',
  `sort_no`     INT         NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='二手分类表';
