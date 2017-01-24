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
        byte[] buffer = new byte[1024];
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

}
