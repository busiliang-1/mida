package cn.iocoder.yudao.module.mate.service.risk;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地敏感词服务（自包含实现，避免外部依赖下载风险；可后续替换为 houbb/sensitive-word）。
 * 归一化（去空格/全角转半角/小写）后做包含匹配，命中返回 HIT 转人工。
 * 词库支持运行时热加载（reload/addWord）。
 */
@Component
public class SensitiveWordService {

    /** 判定结果 */
    public record AuditResult(String verdict, List<String> hits, int dictVersion) {
        public static final String PASS = "PASS";
        public static final String HIT = "HIT";
    }

    private final Set<String> words = ConcurrentHashMap.newKeySet();
    private volatile int dictVersion = 1;

    public SensitiveWordService() {
        // 校园场景默认词库：诈骗话术/诱导转账/引流/违规
        List.of("加微信", "加微", "私下转账", "私下交易", "刷单", "裸聊", "赌博", "博彩",
                "代付", "验证码给我", "向我转账", "先转账", "保证金", "解冻金",
                "色情", "援交", "约炮", "代开发票").forEach(words::add);
    }

    public AuditResult match(String text) {
        if (StrUtil.isBlank(text)) {
            return new AuditResult(AuditResult.PASS, List.of(), dictVersion);
        }
        String normalized = normalize(text);
        List<String> hits = new ArrayList<>();
        for (String w : words) {
            if (normalized.contains(normalize(w))) {
                hits.add(w);
            }
        }
        return new AuditResult(hits.isEmpty() ? AuditResult.PASS : AuditResult.HIT, hits, dictVersion);
    }

    public void addWord(String word) {
        if (StrUtil.isNotBlank(word)) {
            words.add(word.trim());
            dictVersion++;
        }
    }

    public int getDictVersion() {
        return dictVersion;
    }

    public int getWordCount() {
        return words.size();
    }

    /** 去空格、全角转半角、小写 */
    private String normalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c -= 0xFEE0; // 全角转半角
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
