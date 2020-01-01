package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class ClientHello {

    private static final int TLS_HEADER_LENGTH = 5;
    private static final int CH_HEADER_LEN = 6;
    private static final int CH_RANDOM_LEN = 32;
    private static final int SNI_EXTENSION_CODE = 0;
    private static final int SNI_HOST_NAME_CODE = 0;
    private static final int TLS_MESSAGE_BUFFER_SIZE = 16384;
    private static final byte[] EMPTY = new byte[0];

    private InputStream inputStream;

    private String sniHostName = "";
    private TlsVersion protocolVersion = TlsVersion.UNDEFINED;
    private TlsVersion recordProtocolVersion = TlsVersion.UNDEFINED;
    private byte[] sessionId = EMPTY;

    private byte[] buffer = new byte[512];
    private int bufferSize = 0;

    private ClientHello() {

    }

    static ClientHello read(MarkAndResetInputStream inputStream) throws IOException {
        ClientHello clientHello = new ClientHello(inputStream);
        inputStream.mark(0);
        clientHello.parse();
        inputStream.reset();
        clientHello.inputStream = null; // we don't want to keep reference as it might be closed before ClientHello
                                        // object is freed
        clientHello.buffer = null;
        return clientHello;
    }

    public String getSniHostName() {
        return sniHostName;
    }

    public TlsVersion getProtocolVersion() {
        return protocolVersion;
    }

    public TlsVersion getRecordProtocolVersion() {
        return recordProtocolVersion;
    }

    public byte[] getSessionId() {
        return sessionId;
    }

    private ClientHello(MarkAndResetInputStream inputStream) {
        this.inputStream = inputStream;
    }

    private void parse() throws IOException {
        int read = readFromStream(inputStream, buffer, bufferSize, TLS_HEADER_LENGTH);
        if (read < TLS_HEADER_LENGTH) {
            return;
        }
        if (!isTlsHandshake(buffer)) {
            tryReadingAsSsl2();
            return;
        }
        int messageLen = calcMessageLength();
        if (messageLen > TLS_MESSAGE_BUFFER_SIZE) {
            return;
        }
        resizeBufferIfNecessary(messageLen, TLS_HEADER_LENGTH);
        int toRead = messageLen - TLS_HEADER_LENGTH;
        read = readFromStream(inputStream, buffer, TLS_HEADER_LENGTH, toRead);
        if (read < toRead) {
            return;
        }
        processProtocolVersion();
        processSessionId();
        int extensionStartPos = calcExtensionsStartPos();
        if (extensionStartPos < messageLen) {
            processExtentions(extensionStartPos);
        }
    }

    private boolean isTlsHandshake(byte[] buffer) {
        return buffer[0] == 0x16;
    }

    private int calcMessageLength() {
        return getInt16(3) + TLS_HEADER_LENGTH;
    }

    private int getInt8(int pos) {
        if (pos < buffer.length) {
            return (int)buffer[pos] & 0xFF;
        } else {
            return 0;
        }
    }

    private int getInt16(int pos) {
        return (getInt8(pos) << 8) | getInt8(pos + 1);
    }

    private static int readFromStream(InputStream inputStream, byte[] buffer, int offset, int len) throws IOException {
        int result = 0;
        int read;
        do {
            read = inputStream.read(buffer, offset, len);
            result += read;
        } while (read > 0 && result < len);
        return result;
    }

    private void resizeBufferIfNecessary(int requiredLength, int toCopy) {
        if (buffer.length < requiredLength) {
            byte[] newBuffer = new byte[requiredLength];
            System.arraycopy(buffer, 0, newBuffer, 0, toCopy);
            buffer = newBuffer;
        }
    }

    private void processProtocolVersion() {
        recordProtocolVersion = getProtocolVersion(1);
        protocolVersion = getProtocolVersion(TLS_HEADER_LENGTH + 3 + 1);
    }

    private TlsVersion getProtocolVersion(int pos) {
        int major = getInt8(pos);
        int minor = getInt8(pos + 1);
        return new TlsVersion(major, minor);
    }

    private void processSessionId() {
        int pos = TLS_HEADER_LENGTH + CH_HEADER_LEN + CH_RANDOM_LEN;
        int sessionIdLen = getInt8(pos);
        if (sessionIdLen > 0) {
            sessionId = new byte[sessionIdLen];
            System.arraycopy(buffer, pos + 1, sessionId, 0, sessionIdLen);
        }
    }

    private int calcExtensionsStartPos() {
        int pos = TLS_HEADER_LENGTH + CH_HEADER_LEN + CH_RANDOM_LEN;
        int sessionIdLen = getInt8(pos);
        pos += 1 + sessionIdLen;
        int cipherSuiteLen = getInt16(pos);
        pos += 2 + cipherSuiteLen;
        int compressionMethodLen = getInt8(pos);
        pos += 1 + compressionMethodLen;
        return pos;
    }

    private void processExtentions(int startPos) {
        int extLength = getInt16(startPos);
        int pos = startPos + 2;
        while (pos < startPos + extLength) {
            pos += processExtention(pos);
        }
    }

    private int processExtention(int pos) {
        int extType = getInt16(pos);
        int extLen = getInt16(pos + 2);
        if (extType == SNI_EXTENSION_CODE) {
            processSniExtension(pos + 4);
        }
        return 4 + extLen;
    }

    private void processSniExtension(int startPos) {
        int listLength = getInt16(startPos);
        int pos = startPos + 2;
        if (pos < startPos + listLength) {
            processOneSni(pos);
        }
    }

    private int processOneSni(int pos) {
        int nameType = getInt8(pos);
        int nameLen = getInt16(pos + 1);
        if (nameType == SNI_HOST_NAME_CODE) {
            try {
                String name = new String(buffer, pos + 3, nameLen, "ISO_8859_1");
                if (name != null && name.length() > 0) {
                    sniHostName = name;
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        return 3 + nameLen;
    }

    private boolean tryReadingAsSsl2() {
        if ((buffer[0] & 0x80) == 0) {
            return false;
        }
        if (getInt8(2) != 1) {
            return false;
        }
        int major = getInt8(3);
        int minor = getInt8(4);
        if (major != 3 || minor < 0 || minor > 3) {
            return false;
        }
        protocolVersion = new TlsVersion(major, minor);
        recordProtocolVersion = new TlsVersion(2, 0);
        return true;
    }
}
