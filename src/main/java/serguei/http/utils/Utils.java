package serguei.http.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.regex.Pattern;

public final class Utils {

    private static final String IPV4_PATTERN_STRING = "(([1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){1}"
            + "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){2}" + "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])";
    private static final Pattern IPV4_PATTERN = Pattern.compile("^" + IPV4_PATTERN_STRING + "$");
    private static final Pattern IPV6_STD_PATTERN = Pattern.compile("^[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4}){7}$");
    private static final String IPV6_COMPRESSED_PATTERN_STR = "(([0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,5})?)::(([0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,5})?)";
    private static final Pattern IPV6_COMPRESSED_PATTERN = Pattern.compile("^" + IPV6_COMPRESSED_PATTERN_STR + "$");
    private static final int IPV6_COLON_COUNT = 7;

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

    public static String toString(InputStream inputStream, String charsetName) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(charsetName);
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

    public static boolean isIPv4Address(String value) {
        return IPV4_PATTERN.matcher(value).matches();
    }

    public static boolean isIPv6Address(String value) {
        String host = value;
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return IPV6_STD_PATTERN.matcher(host).matches() || isIPv6HexCompressedAddress(host);
    }

    public static X509Certificate readPemFile(InputStream inputStream) throws IOException, CertificateException {
        StringBuilder data = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.contains("BEGIN CERTIFICATE") && !line.contains("END CERTIFICATE")) {
                data.append(line);
            }
        }
        byte[] certBytes = Base64.getDecoder().decode(data.toString());
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate)factory.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    private static boolean isIPv6HexCompressedAddress(String value) {
        int colonCount = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == ':') {
                colonCount++;
            }
        }
        return colonCount <= IPV6_COLON_COUNT && IPV6_COMPRESSED_PATTERN.matcher(value).matches();
    }

}
