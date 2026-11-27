-- M5 内容社区（对齐芋道 BaseDO：creator/create_time/updater/update_time/deleted）
-- 契约来源：数据库文档-后端乙.md（业务字段/唯一约束按文档，审计列按甲的 BaseDO 适配）
-- 说明：契约表未定义 like_count/comment_count/collect_count 列，计数一律由关系表 COUNT 派生
--       （热点读由 Redis 缓存 30s 兜住），不臆造计数列，避免与审核态/发布态双列口径冲突。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `moment_post` (
  `post_id`        BIGINT        NOT NULL COMMENT '雪花主键',
  `user_id`        BIGINT        NOT NULL COMMENT '发布者',
  `content`        VARCHAR(2000) NULL COMMENT '正文文本',
  `topic_id`       INT           NULL COMMENT '话题编号',
  `audit_status`   TINYINT       NOT NULL DEFAULT 0 COMMENT '0待审1通过2驳回3转人工',
  `publish_status` TINYINT       NOT NULL DEFAULT 0 COMMENT '0仅自己可见1已发布2已下架',
  `visible_scope`  TINYINT       NOT NULL DEFAULT 0 COMMENT '0公开1同校2仅自己',
  `creator`        VARCHAR(64)   NULL DEFAULT '' COMMENT '创建者',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `updater`        VARCHAR(64)   NULL DEFAULT '' COMMENT '更新者',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        BIT(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`post_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_publish_time` (`publish_status`, `create_time`),
  KEY `idx_audit` (`audit_status`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态内容表';

CREATE TABLE IF NOT EXISTS `moment_attachment` (
  `attachment_id` BIGINT       NOT NULL COMMENT '雪花主键',
  `post_id`       BIGINT       NOT NULL COMMENT '动态ID',
  `object_key`    VARCHAR(255) NOT NULL COMMENT '对象存储键,不存完整URL',
  `media_type`    TINYINT      NOT NULL DEFAULT 1 COMMENT '1图片2视频封面',
  `width`         INT          NULL COMMENT '原始宽度',
  `height`        INT          NULL COMMENT '原始高度',
  `sort_no`       TINYINT      NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `creator`       VARCHAR(64)  NULL DEFAULT '' COMMENT '创建者',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `updater`       VARCHAR(64)  NULL DEFAULT '' COMMENT '更新者',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       BIT(1)       NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`attachment_id`),
  UNIQUE KEY `uk_post_sort` (`post_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态附件表';

CREATE TABLE IF NOT EXISTS `moment_like` (
  `id`          BIGINT      NOT NULL COMMENT '雪花主键',
  `post_id`     BIGINT      NOT NULL COMMENT '动态ID',
  `user_id`     BIGINT      NOT NULL COMMENT '点赞者',
  `creator`     VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  `updater`     VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)      NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_post` (`post_id`),
  KEY `idx_user_post` (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态点赞表';

CREATE TABLE IF NOT EXISTS `moment_comment` (
  `comment_id`   BIGINT       NOT NULL COMMENT '雪花主键',
  `post_id`      BIGINT       NOT NULL COMMENT '动态ID',
  `user_id`      BIGINT       NOT NULL COMMENT '评论者',
  `parent_id`    BIGINT       NULL COMMENT '父评论,两级',
  `content`      VARCHAR(500) NOT NULL COMMENT '评论内容,进审核',
  `audit_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审1通过2驳回',
  `creator`      VARCHAR(64)  NULL DEFAULT '' COMMENT '创建者',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  `updater`      VARCHAR(64)  NULL DEFAULT '' COMMENT '更新者',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      BIT(1)       NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`comment_id`),
  KEY `idx_post` (`post_id`, `comment_id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态评论表';

CREATE TABLE IF NOT EXISTS `moment_collect` (
  `id`          BIGINT      NOT NULL COMMENT '雪花主键',
  `post_id`     BIGINT      NOT NULL COMMENT '动态ID',
  `user_id`     BIGINT      NOT NULL COMMENT '收藏者',
  `creator`     VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `updater`     VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)      NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user_post` (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态收藏表';

CREATE TABLE IF NOT EXISTS `user_follow` (
  `id`          BIGINT      NOT NULL COMMENT '雪花主键',
  `follower_id` BIGINT      NOT NULL COMMENT '关注者',
  `followee_id` BIGINT      NOT NULL COMMENT '被关注者',
  `creator`     VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  `updater`     VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)      NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`),
  KEY `idx_followee` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='关注关系表';

CREATE TABLE IF NOT EXISTS `homepage_config` (
  `config_id`    BIGINT       NOT NULL COMMENT '雪花主键',
  `user_id`      BIGINT       NOT NULL COMMENT '用户ID,唯一',
  `cover_key`    VARCHAR(255) NULL COMMENT '封面图对象键',
  `signature`    VARCHAR(100) NULL COMMENT '个性签名',
  `modules_json` JSON         NOT NULL COMMENT '模块列表/开关/顺序',
  `creator`      VARCHAR(64)  NULL DEFAULT '' COMMENT '创建者',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次配置',
  `updater`      VARCHAR(64)  NULL DEFAULT '' COMMENT '更新者',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      BIT(1)       NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='主页配置表';
