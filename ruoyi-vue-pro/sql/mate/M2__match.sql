-- M2 搭子匹配（对齐芋道 BaseDO）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `mate_demand` (
  `demand_id`     BIGINT        NOT NULL COMMENT '雪花主键',
  `publisher_id`  BIGINT        NOT NULL COMMENT '发布者',
  `activity_type` VARCHAR(16)   NOT NULL COMMENT '运动/学习/餐饮/出行/拼单',
  `title`         VARCHAR(60)   NOT NULL COMMENT '标题',
  `description`   VARCHAR(1000) NULL COMMENT '说明',
  `start_time`    DATETIME      NOT NULL COMMENT '活动开始',
  `end_time`      DATETIME      NULL COMMENT '活动结束',
  `location_name` VARCHAR(120)  NOT NULL COMMENT '地点',
  `longitude`     DECIMAL(10,7) NULL COMMENT '经度GCJ-02',
  `latitude`      DECIMAL(10,7) NULL COMMENT '纬度',
  `city_code`     CHAR(6)       NOT NULL COMMENT '城市码',
  `need_count`    TINYINT       NOT NULL COMMENT '需要人数2-20含本人',
  `joined_count`  TINYINT       NOT NULL DEFAULT 1 COMMENT '已通过人数',
  `gender_limit`  TINYINT       NOT NULL DEFAULT 0 COMMENT '0不限1男2女',
  `auth_limit`    TINYINT       NOT NULL DEFAULT 1 COMMENT '报名者最低等级',
  `fee_mode`      TINYINT       NOT NULL DEFAULT 0 COMMENT '0免费1均摊2承担',
  `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '0草稿1审核中2招募中3已成团4进行中5已结束6已关闭',
  `creator`       VARCHAR(64)   NULL DEFAULT '',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`       VARCHAR(64)   NULL DEFAULT '',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       BIT(1)        NOT NULL DEFAULT b'0',
  PRIMARY KEY (`demand_id`),
  KEY `idx_city_status` (`city_code`,`status`,`create_time`),
  KEY `idx_publisher` (`publisher_id`,`status`),
  KEY `idx_type_start` (`activity_type`,`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='搭子需求单';

CREATE TABLE IF NOT EXISTS `mate_signup` (
  `signup_id`       BIGINT      NOT NULL,
  `demand_id`       BIGINT      NOT NULL,
  `user_id`         BIGINT      NOT NULL,
  `message`         VARCHAR(200) NULL,
  `audit_status`    TINYINT     NOT NULL DEFAULT 0 COMMENT '0待审1通过2拒绝3退出4关闭',
  `credit_snapshot` SMALLINT    NULL,
  `creator`         VARCHAR(64) NULL DEFAULT '',
  `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`         VARCHAR(64) NULL DEFAULT '',
  `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         BIT(1)      NOT NULL DEFAULT b'0',
  PRIMARY KEY (`signup_id`),
  UNIQUE KEY `uk_demand_user` (`demand_id`,`user_id`),
  KEY `idx_demand` (`demand_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报名记录';

CREATE TABLE IF NOT EXISTS `mate_like` (
  `like_id`     BIGINT      NOT NULL,
  `liker_id`    BIGINT      NOT NULL,
  `likee_id`    BIGINT      NOT NULL,
  `action`      TINYINT     NOT NULL COMMENT '1喜欢2跳过',
  `creator`     VARCHAR(64) NULL DEFAULT '',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`     VARCHAR(64) NULL DEFAULT '',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     BIT(1)      NOT NULL DEFAULT b'0',
  PRIMARY KEY (`like_id`),
  UNIQUE KEY `uk_liker_likee` (`liker_id`,`likee_id`),
  KEY `idx_likee` (`likee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='喜欢/跳过记录';

CREATE TABLE IF NOT EXISTS `mate_match` (
  `match_id`       BIGINT      NOT NULL,
  `user_low_id`    BIGINT      NOT NULL COMMENT '两方较小ID',
  `user_high_id`   BIGINT      NOT NULL COMMENT '两方较大ID',
  `source`         TINYINT     NOT NULL COMMENT '1滑动2成团3附近',
  `greeting_used`  TINYINT     NOT NULL DEFAULT 0,
  `cooldown_until` DATETIME    NULL,
  `session_id`     BIGINT      NULL,
  `creator`        VARCHAR(64) NULL DEFAULT '',
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`        VARCHAR(64) NULL DEFAULT '',
  `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        BIT(1)      NOT NULL DEFAULT b'0',
  PRIMARY KEY (`match_id`),
  UNIQUE KEY `uk_low_high` (`user_low_id`,`user_high_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='双向匹配关系';
