package serguei.http.utils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public final class Utils {

    private Utils() {

    }

    public static byte[] concat(byte[]... arrays) {
        int len = 0;
        for (byte[] array : arrays) {
            len += array.length;
        }
        int pos = 0;
        byte[] result = new byte[len];
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, pos, array.length);
            pos += array.length;
        }
        return result;
    }

    public static String concatWithDelimiter(String[] strings, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (String str : strings) {
            if (builder.length() > 0) {
                builder.append(delimiter);
            }
            builder.append(str);
        }
        return builder.toString();
    }

    public static String encodeHtml(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"') {
                result.append("&quot;");
            } else if (ch == '&') {
                result.append("&amp;");
            } else if (ch == '<') {
                result.append("&lt;");
            } else if (ch == '>') {
                result.append("&gt;");
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    public static void closeQuietly(Closeable closable) {
        if (closable != null) {
            try {
                closable.close();
            } catch (IOException e) {
                // quietly
            }
        }
    }

    public static byte[] readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toByteArray();
    }

    public static String multiplyString(String value, int number) {
        StringBuilder builder = new StringBuilder(value.length() * number);
        for (int i = 0; i < number; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    public static byte[] buildDataArray(int len) {
        byte[] pattern = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
                'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
                'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = pattern[i % pattern.length];
        }
        return result;
    }

    public static void drainStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int read = inputStream.read(buffer);
        while (read >= 0) {
            read = inputStream.read(buffer);
        }
    }

}
