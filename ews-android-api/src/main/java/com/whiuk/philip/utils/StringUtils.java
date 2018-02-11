package com.whiuk.philip.utils;


public class StringUtils {
    public static boolean isEmpty(String stringValue) {
        return stringValue == null || stringValue.isEmpty();
    }

    public static boolean equals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }
}
