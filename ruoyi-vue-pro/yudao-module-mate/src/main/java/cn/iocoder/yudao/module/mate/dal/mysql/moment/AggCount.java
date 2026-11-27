package cn.iocoder.yudao.module.mate.dal.mysql.moment;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关系表聚合投影（post_id → count）。
 * 之所以不落 like_count 列：契约表（数据库文档 M5 段）未定义计数列，凭空加列会让
 * "审核态/发布态双列"之外再多一份可能不一致的事实来源。计数一律由关系表 COUNT 派生，
 * 批量场景用 GROUP BY 一次取回，避免逐条 COUNT 放大成 N+1。
 */
@Data
@NoArgsConstructor
public class AggCount {

    private Long id;
    private Long cnt;
}
