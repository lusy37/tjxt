package com.tianji.promotion;

import cn.hutool.core.util.RandomUtil;
import com.tianji.promotion.utils.AESUtil;
import com.tianji.promotion.utils.Base32;
import com.tianji.promotion.utils.BitConverter;
import com.tianji.promotion.utils.CodeUtil;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class EncryptTest {
    private static final String CIPHER_ALGORITHM = "AES/CTR/NoPadding";
    private AESUtil aesUtil = new AESUtil();
    @Test
    void testRC4() throws NoSuchAlgorithmException, Exception {
        // 序列号
        long serialNum = 1225L;
        // 利用hmac对序列化加密
        long random = RandomUtil.randomInt(0xff);

        // 拼接，序列号（32位） + 随机码的后8位
        long rawCode = serialNum << 8 | (random & 0xFF);
        System.out.println("rawCode = " + rawCode);

        byte[] raw = BitConverter.getBytes(rawCode);
        String encode = Base32.encode(aesUtil.encrypt(Arrays.copyOf(raw, 5)));
        System.out.println("encode = " + encode);

        byte[] bytes = Base32.decode2Byte(encode);
        long rawNum = BitConverter.toLong(Arrays.copyOf(aesUtil.decrypt(bytes), 8));
        System.out.println("rawNum = " + rawNum);
        random = rawNum & 0xFF;
        System.out.println("random = " + random);
        serialNum = rawNum >>> 8;
        System.out.println("serialNum = " + serialNum);
    }

    @Test
    void testBase32() {
        byte[] raw = BitConverter.getBytes(203760602714L);
        byte[] raw1 = Arrays.copyOf(raw, 5);
        byte[] raw2 = aesUtil.encrypt(raw1);
        System.out.println("raw2.length = " + raw2.length);
        System.out.println("raw2:" + Arrays.toString(raw2));
        String encode = Base32.encode(raw2);
        System.out.println("encode = " + encode);

        byte[] bytes = Base32.decode2Byte(encode);
        System.out.println("bytes.length = " + bytes.length);
        System.out.println("bytes:" + Arrays.toString(bytes));
        byte[] decrypt = aesUtil.decrypt(bytes);
        System.out.println("decrypt.length = " + decrypt.length);
        System.out.println("decrypt:" + Arrays.toString(decrypt));
        System.out.println("decode = " + BitConverter.toLong(Arrays.copyOf(decrypt, 8)));

        long l = 203760602714L;
        String e = Base32.encode(l);
        System.out.println("e = " + e);
        long d = Base32.decode(e);
        System.out.println("d = " + d);

    }

    @Test
    void testCodeUtil() {
        CodeUtil codeUtil = new CodeUtil(aesUtil);

        String code = codeUtil.generateCode(1124);
        System.out.println("code = " + code);

        long num = codeUtil.parseCode(code);
        System.out.println("num = " + num);
    }
}
