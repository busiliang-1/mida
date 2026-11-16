package cn.iocoder.yudao.module.mate.service.risk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地敏感词匹配测试：命中/通过/归一化(空格+全角+大小写)/热加载。
 */
class SensitiveWordServiceTest {

    private final SensitiveWordService service = new SensitiveWordService();

    @Test
    void hitDirect() {
        SensitiveWordService.AuditResult r = service.match("想私下转账给你");
        assertEquals(SensitiveWordService.AuditResult.HIT, r.verdict());
        assertTrue(r.hits().contains("私下转账"));
    }

    @Test
    void hitWithSpacesAndFullWidth() {
        // 空格分隔 + 全角字符应仍命中
        SensitiveWordService.AuditResult r = service.match("加 微 信 私聊");
        assertEquals(SensitiveWordService.AuditResult.HIT, r.verdict());
    }

    @Test
    void passNormal() {
        SensitiveWordService.AuditResult r = service.match("周五晚上打羽毛球缺两人");
        assertEquals(SensitiveWordService.AuditResult.PASS, r.verdict());
        assertTrue(r.hits().isEmpty());
    }

    @Test
    void hotReloadAddsWord() {
        int v0 = service.getDictVersion();
        assertEquals(SensitiveWordService.AuditResult.PASS, service.match("羽毛球").verdict());
        service.addWord("羽毛球");
        assertEquals(SensitiveWordService.AuditResult.HIT, service.match("羽毛球").verdict());
        assertEquals(v0 + 1, service.getDictVersion());
    }
}
