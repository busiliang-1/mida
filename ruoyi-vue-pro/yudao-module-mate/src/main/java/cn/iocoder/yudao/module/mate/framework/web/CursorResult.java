package cn.iocoder.yudao.module.mate.framework.web;

import java.util.List;

/**
 * 游标分页的 HTTP 响应体。字段名与接口文档一致：list / nextCursor / hasMore。
 */
public record CursorResult<T>(List<T> list, String nextCursor, Boolean hasMore) {

    public static <T> CursorResult<T> of(CursorPage<T> page) {
        return new CursorResult<>(page.list(), page.nextCursor(), page.hasMore());
    }
}
