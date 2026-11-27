package cn.iocoder.yudao.module.mate.framework.web;

import java.util.List;

/**
 * 游标分页结果。项目铁律：列表一律游标，禁 limit/offset 页码。
 *
 * @param list       当前页数据
 * @param nextCursor 下一页游标；{@code null} 表示没有更多
 * @param hasMore    是否还有更多
 */
public record CursorPage<T>(List<T> list, String nextCursor, boolean hasMore) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
