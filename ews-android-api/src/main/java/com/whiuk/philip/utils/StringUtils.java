package com.whiuk.philip.utils;


import java.io.UnsupportedEncodingException;


public class StringUtils {
    public static boolean isEmpty(String stringValue) {
        return stringValue == null || stringValue.isEmpty();
    }

    public static boolean equals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    public static byte[] getBytesUtf8(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public static boolean isBlank(String olsonTimeZoneId) {
        return olsonTimeZoneId.isEmpty();
    }
}
