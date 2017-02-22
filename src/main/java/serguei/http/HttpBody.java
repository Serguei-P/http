package serguei.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPInputStream;

import serguei.http.utils.Utils;

class HttpBody {

    static final String BODY_CODEPAGE = "UTF-8";
    private static final int BUFFER_SIZE = 1024 * 4;

    private final InputStream bodyInputStream;
    private final InputStream streamToDrainOfData;
    private final boolean hasBody;

    HttpBody(InputStream inputStream, long contentLength, boolean chunked, String encoding, boolean allowUnknownBodyLength)
            throws IOException {
        InputStream stream = inputStream;
        if (chunked) {
            stream = new ChunkedInputStream(inputStream);
        } else if (contentLength > 0) {
            stream = new LimitedLengthInputStream(inputStream, contentLength);
        } else {
            stream = inputStream;
        }
        if (encoding != null && encoding.equals("gzip")) {
            // GZIPInputStream returns -1 before all bytes from input stream read
            streamToDrainOfData = stream;
            stream = new GZIPInputStream(stream);
        } else {
            streamToDrainOfData = null;
        }
        this.bodyInputStream = stream;
        this.hasBody = contentLength > 0 || chunked || (allowUnknownBodyLength && contentLength < 0);
    }

    String readAsString() throws IOException {
        byte[] buffer = readAsBytes();
        return new String(buffer, BODY_CODEPAGE);
    }

    byte[] readAsBytes() throws IOException {
        if (hasBody) {
            return readStream(bodyInputStream);
        } else {
            return new byte[0];
        }
    }

    InputStream getBodyInputStream() {
        return bodyInputStream;
    }

    static byte[] stringAsBytes(String value) {
        try {
            return value.getBytes(BODY_CODEPAGE);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("charset " + BODY_CODEPAGE + " is not supported", e);
        }
    }

    static String bytesAsString(byte[] value) {
        try {
            return new String(value, BODY_CODEPAGE);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("charset " + BODY_CODEPAGE + " is not supported", e);
        }
    }

    private byte[] readStream(InputStream stream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read = stream.read(buffer);
        while (read > 0) {
            outputStream.write(buffer, 0, read);
            read = stream.read(buffer);
        }
        byte[] result = outputStream.toByteArray();
        if (streamToDrainOfData != null) {
            Utils.readFully(streamToDrainOfData);
        }
        return result;
    }

}
