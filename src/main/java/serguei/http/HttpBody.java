package serguei.http;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import serguei.http.utils.Utils;

class HttpBody {

    static final String BODY_CODEPAGE = "UTF-8";
    private static final int BUFFER_SIZE = 1024 * 4;

    private final InputStream bodyInputStream;
    private final boolean hasBody;
    private boolean compressed;
    private final String encoding;
    private InputStream userFacingStream;

    HttpBody(InputStream inputStream, long contentLength, boolean chunked, String encoding, boolean allowUnknownBodyLength)
            throws IOException {
        this.encoding = encoding;
        this.hasBody = contentLength > 0 || chunked || (allowUnknownBodyLength && contentLength < 0);
        if (chunked) {
            bodyInputStream = new ChunkedInputStream(inputStream);
        } else if (contentLength > 0) {
            bodyInputStream = new LimitedLengthInputStream(inputStream, contentLength);
        } else {
            bodyInputStream = inputStream;
        }
        compressed = encoding != null
                && (encoding.equals("gzip") || encoding.equals("deflate") || getNonstandardStreamFactory(encoding) != null);
    }

    boolean hasBody() {
        return hasBody;
    }

    String readAsString() throws IOException {
        byte[] buffer = readAsBytes();
        return new String(buffer, BODY_CODEPAGE);
    }

    byte[] readAsBytes() throws IOException {
        if (hasBody) {
            return readStream(getBodyInputStream());
        } else {
            return new byte[0];
        }
    }

    InputStream getBodyInputStream() throws IOException {
        if (userFacingStream == null) {
            userFacingStream = setupDecompressionIfRequired();
        }
        return userFacingStream;
    }

    InputStream getOriginalBodyInputStream() {
        if (userFacingStream == null) {
            userFacingStream = new UserFacingInputStream(bodyInputStream, null);
        }
        return userFacingStream;
    }

    void drain() throws IOException {
        if (hasBody) {
            if (userFacingStream != null) {
                userFacingStream.close();
            } else {
                Utils.drainStream(bodyInputStream);
            }
        }
    }

    boolean isCompressed() {
        return compressed;
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
        return outputStream.toByteArray();
    }

    private boolean isGzip(InputStream input) throws IOException {
        int ch1 = input.read();
        if (ch1 == -1) {
            throw new EOFException();
        }
        int ch2 = input.read();
        if (ch2 == -1) {
            throw new EOFException();
        }
        int number = (ch2 << 8) | ch1;
        return number == GZIPInputStream.GZIP_MAGIC;
    }

    private InputStreamWrapperFactory getNonstandardStreamFactory(String encoding) {
        if (encoding != null) {
            return Http.getWrapperFactoryForContentEncoding(encoding);
        } else {
            return null;
        }
    }

    private UserFacingInputStream setupDecompressionIfRequired() throws IOException {
        InputStream streamToDrainOfData = null;
        InputStream stream = bodyInputStream;
        if (encoding != null && encoding.equals("gzip")) {
            // GZIPInputStream returns -1 before all bytes from input stream read
            stream = new MarkAndResetInputStream(stream);
            stream.mark(0);
            // authors of some sites forget to actually gzip the body while adding a header
            if (isGzip(stream)) {
                stream.reset();
                streamToDrainOfData = stream;
                stream = new GZIPInputStream(stream);
            } else {
                stream.reset();
                streamToDrainOfData = null;
                compressed = false;
            }
        } else if (encoding != null && encoding.equals("deflate")) {
            // DeflateInputStream returns -1 before all bytes from input stream read
            streamToDrainOfData = stream;
            stream = new InflaterInputStream(stream);
        } else {
            InputStreamWrapperFactory streamFactory = getNonstandardStreamFactory(encoding);
            if (streamFactory != null) {
                streamToDrainOfData = stream;
                stream = streamFactory.wrap(stream);
            } else {
                streamToDrainOfData = null;
            }
        }
        return new UserFacingInputStream(stream, streamToDrainOfData);
    }

}
