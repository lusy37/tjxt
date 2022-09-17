package com.tianji.promotion.utils;

import com.tianji.common.constants.RegexConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static com.tianji.promotion.constants.PromotionErrorInfo.INVALID_COUPON_CODE;

/**
 * <p>兑换码算法说明：</p>
 * <p>兑换码分为明文和密文，明文是40位二进制数，密文是长度为8的Base32编码的字符串 </p>
 * <p>兑换码的明文结构：</p>
 * <p> 32(序列号) + 8(校验码)</p>
 *
 * <p>兑换码的加密过程：</p>
 * <p> - 首先利用由序列号s的低8位 异或 序列号的高8位 得到8位校验码 c
 *     - 然后拼接兑换码明文：序列号s（高32位） + 校验码c（低8位）
 *     - 利用AES加密明文，得到密文
 *     - 利用Base32对密文转码，生成兑换码
 * </p>
 * <p>兑换码的解密过程：</p>
 * <p>  - 首先利用Base32解码兑换码，得到字节
 *      - 基于AES解密，并转为40位二进制数
 *      - 取低8位，得到校验码 c
 *      - 取高32位，得到序列号 s
 *      - 验证格式：用户 序列号s的低8位 异或 序列号s的高8位 得到的结果与c比较，如果不一致，说明是无效数据
 * </p>
 *
 */
@Component
@RequiredArgsConstructor
public class CodeUtil {

    private final AESUtil aesUtil;

    /**
     * 生成兑换码
     * @param serialNum 递增序列号
     * @return 兑换码
     */
    public String generateCode(long serialNum){
        // 1.生成校验码，由序列号的低8位 异或 序列号的高8位
        long checkCode = (serialNum >>> 24 & 0xff) ^ (serialNum & 0xff);
        // 2.拼接兑换码明文：序列号（32位） + 校验码（8位）
        long code = serialNum << 8L | (checkCode & 0xff);
        // 3.加密
        byte[] digest = aesUtil.encrypt(Arrays.copyOf(BitConverter.getBytes(code), 5));
        // 4.转码
        return Base32.encode(digest);
    }

    public long parseCode(String code){
        if (StringUtils.isEmpty(code) || !code.matches(RegexConstants.COUPON_CODE_PATTERN)) {
            // 兑换码格式错误
            throw new BadRequestException(INVALID_COUPON_CODE);
        }
        // 1.Base32解码
        byte[] bytes = Base32.decode2Byte(code);
        // 2.aes解密
        byte[] decrypt = aesUtil.decrypt(bytes);
        // 3.转为数字
        long num = BitConverter.toLong(Arrays.copyOf(decrypt, 8));
        // 4.获取低8位，校验码
        int checkCode = (int) (num & 0xff);
        // 5.获取高32位，序列号
        long serialNum = num >>> 8 & 0xffffffffL;
        // 6.验证格式：
        if (((serialNum >>> 24 & 0xff) ^ (serialNum & 0xff)) != checkCode) {
            throw new BadRequestException(INVALID_COUPON_CODE);
        }
        return serialNum;
    }
}
