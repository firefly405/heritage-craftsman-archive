package com.qizixuan.heritage.common;

import java.util.regex.Pattern;

public final class PhoneNumbers {
    private static final Pattern MAINLAND_MOBILE = Pattern.compile("1[3-9]\\d{9}");

    private PhoneNumbers() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String phone = value.trim();
        if (!isValid(phone)) throw new BusinessException("请输入正确的11位中国大陆手机号");
        return phone;
    }

    public static boolean isValid(String value) {
        return value != null && MAINLAND_MOBILE.matcher(value.trim()).matches();
    }
}
