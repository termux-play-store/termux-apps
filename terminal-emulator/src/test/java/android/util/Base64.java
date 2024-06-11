package android.util;

public class Base64 {

    public static String encodeToString(byte[] input, int flags) {
        if (flags != 0) {
            throw new RuntimeException("Unsupported flags: " + flags);
        }
        return java.util.Base64.getEncoder().encodeToString(input);
    }

    public static byte[] decode(String str, int flags) {
        if (flags != 0) {
            throw new RuntimeException("Unsupported flags: " + flags);
        }
        return java.util.Base64.getDecoder().decode(str);
    }
}
