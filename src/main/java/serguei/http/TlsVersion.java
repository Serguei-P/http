package serguei.http;

import sun.security.ssl.ProtocolVersion;

@SuppressWarnings("restriction")
public class TlsVersion {

    protected static final TlsVersion UNDEFINED = new TlsVersion(0, 0);
    public static final TlsVersion SSLv3 = new TlsVersion(3, 0);
    public static final TlsVersion TLSv10 = new TlsVersion(3, 1);
    public static final TlsVersion TLSv11 = new TlsVersion(3, 2);
    public static final TlsVersion TLSv12 = new TlsVersion(3, 3);

    public static final TlsVersion SSLv2Hello = new TlsVersion(2, 0) {

        @Override
        public String toJdkString() {
            return "SSLv2Hello";
        }
    };

    private final int major;
    private final int minor;
    private final String jdkString;

    public TlsVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
        this.jdkString = ProtocolVersion.valueOf(major, minor).toString();
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public boolean higherOrEqualTo(TlsVersion protocolVersion) {
        if (major == protocolVersion.major) {
            return minor >= protocolVersion.minor;
        } else {
            return major >= protocolVersion.major;
        }
    }

    public Integer getCode() {
        if (major > 0 && major <= 9 && minor >= 0 && minor <= 9) {
            return major * 10 + minor;
        } else {
            return null;
        }
    }

    public boolean isUndefined() {
        return major == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TlsVersion) {
            TlsVersion other = (TlsVersion)obj;
            return this.major == other.major && this.minor == other.minor;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return major * 10 + minor;
    }

    @Override
    public String toString() {
        return Integer.toString(major) + "." + Integer.toString(minor);
    }

    public String toJdkString() {
        return jdkString;
    }

    public static String[] toJdkStrings(TlsVersion[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].toJdkString();
        }
        return result;
    }

    public static TlsVersion fromJdkString(String protocolName) {
        if (protocolName.equals(TLSv12.toJdkString())) {
            return TLSv12;
        } else if (protocolName.equals(TLSv11.toJdkString())) {
            return TLSv11;
        } else if (protocolName.equals(TLSv10.toJdkString())) {
            return TLSv10;
        } else if (protocolName.equals(SSLv3.toJdkString())) {
            return SSLv3;
        } else if (protocolName.equals(SSLv2Hello.toJdkString())) {
            return SSLv2Hello;
        } else if (protocolName.startsWith("TLSv1.")) {
            String minorNumberStr = protocolName.substring(6);
            try {
                int minor = Integer.parseInt(minorNumberStr) + 1;
                return new TlsVersion(3, minor);
            } catch (NumberFormatException e) {
                return UNDEFINED;
            }
        } else {
            return UNDEFINED;
        }
    }

}
