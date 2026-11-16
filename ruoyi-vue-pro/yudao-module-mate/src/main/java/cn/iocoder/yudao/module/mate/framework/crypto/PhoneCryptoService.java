package cn.iocoder.yudao.module.mate.framework.crypto;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 手机号敏感字段加解密组件。
 * - phone_cipher：AES 密文（可逆，用于展示脱敏前的还原）。
 * - phone_hash：HMAC-SHA256（不可逆，用于登录等值查询，建唯一索引）。
 * 密钥来自配置 yudao.mate.crypto.key，生产经密钥管理下发；此处为学习项目本地密钥。
 */
@Component
public class PhoneCryptoService {

    private final AES aes;
    private final byte[] hmacKey;

    public PhoneCryptoService(@Value("${yudao.mate.crypto.key:mida-dev-crypto-key-2026}") String key) {
        // AES 密钥取 key 的 SHA-256 前 16 字节，保证长度合法
        byte[] digest = SecureUtil.sha256().digest(key.getBytes(StandardCharsets.UTF_8));
        byte[] aesKey = new byte[16];
        System.arraycopy(digest, 0, aesKey, 0, 16);
        this.aes = SecureUtil.aes(aesKey);
        this.hmacKey = digest;
    }

    public String encrypt(String phone) {
        return aes.encryptHex(phone);
    }

    public String decrypt(String cipher) {
        return aes.decryptStr(cipher);
    }

    public String hash(String phone) {
        return SecureUtil.hmacSha256(hmacKey).digestHex(phone);
    }
}
