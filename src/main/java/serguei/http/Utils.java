package serguei.http;

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

}
