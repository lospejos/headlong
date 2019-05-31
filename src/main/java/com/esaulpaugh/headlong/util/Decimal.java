package com.esaulpaugh.headlong.util;

public final class Decimal {

    private static final int NUM_BYTE_VALS = 1 << Byte.SIZE;
    private static final int CHARS_PER_BYTE = "255".length();

    private static final char[] ENCODING = new char[NUM_BYTE_VALS * CHARS_PER_BYTE];

    static {
        int j = 0;
        for (int i = 0; i < NUM_BYTE_VALS; i++) {
            String str = String.valueOf(i);
            final int len = str.length();
            ENCODING[j++] = len == CHARS_PER_BYTE ? str.charAt(0) : '0';
            ENCODING[j++] = len >= 2 ? str.charAt(len - 2) : '0';
            ENCODING[j++] = str.charAt(len - 1);
        }
    }

    public static String encodeToString(byte[] buffer, int i, final int len) {
        char[] chars = new char[len * CHARS_PER_BYTE];
        final int end = i + len;
        int j = 0;
        for ( ; i < end; i++) {
            int idx = (buffer[i] & 0xFF) * CHARS_PER_BYTE;
            chars[j++] = ENCODING[idx++];
            chars[j++] = ENCODING[idx++];
            chars[j++] = ENCODING[idx];
        }
        return new String(chars);
    }

    public static byte[] decode(String src, int i, final int len) {
        if(len % CHARS_PER_BYTE != 0) {
            throw new IllegalArgumentException("length must be a multiple of " + CHARS_PER_BYTE);
        }
        final int byteLen = len / CHARS_PER_BYTE;
        byte[] bytes = new byte[byteLen];
        for (int j = 0; j < byteLen; j++) {
            bytes[j] = toByte(src.charAt(i++), src.charAt(i++), src.charAt(i++));
        }
        return bytes;
    }

    private static byte toByte(char a, char b, char c) {
        if (a >= '0' && a <= '9'
         && b >= '0' && b <= '9'
         && c >= '0' && c <= '9') {
            return (byte) (
                    ((a - '0') * 100)
                  + ((b - '0') * 10)
                  + (c - '0')
            );
        }
        throw new IllegalArgumentException("illegal digit");
    }
}
