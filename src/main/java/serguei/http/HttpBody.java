package serguei.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

import serguei.http.utils.Utils;

class HttpBody {

    static final Charset BODY_CHARSET = StandardCharsets.UTF_8;
    static final String BODY_CODEPAGE = BODY_CHARSET.name();
    private static final int BUFFER_SIZE = 1024 * 4;

    private final InputStream bodyInputStream;
    private final boolean hasBody;
    private boolean compressed;
    private final String encoding;
    private InputStream userFacingStream;

    HttpBody(InputStream inputStream, long contentLength, boolean chunked, String encoding, boolean allowUnknownBodyLength) {
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
        return new String(buffer, BODY_CHARSET);
    }

    byte[] readAsBytes() throws IOException {
        if (hasBody) {
            return readStream(getBodyInputStream());
        } else {
            return new byte[0];
        }
    }

    InputStream getBodyInputStream() throws IOException {
        if (hasBody) {
            if (userFacingStream == null) {
                userFacingStream = setupDecompressionIfRequired();
            }
            return userFacingStream;
        } else {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    InputStream getOriginalBodyInputStream() {
        if (hasBody) {
            if (userFacingStream == null) {
                userFacingStream = new UserFacingInputStream(bodyInputStream, null);
            }
            return userFacingStream;
        } else {
            return new ByteArrayInputStream(new byte[0]);
        }
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
        return value.getBytes(BODY_CHARSET);
    }

    static String bytesAsString(byte[] value) {
        return new String(value, BODY_CHARSET);
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
        InputStream streamToDrainOfData;
        InputStream stream;
        if (encoding != null && encoding.equals("gzip")) {
            // GZIPInputStream returns -1 before all bytes from input stream read
            stream = new MarkAndResetInputStream(bodyInputStream);
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
            stream = new MarkAndResetInputStream(bodyInputStream);
            stream.mark(0);
            boolean wrapped = isDeflatedStreamWrapped(stream);
            stream.reset();
            streamToDrainOfData = stream;
            stream = new InflaterInputStream(stream, new Inflater(!wrapped));
        } else {
            InputStreamWrapperFactory streamFactory = getNonstandardStreamFactory(encoding);
            if (streamFactory != null) {
                streamToDrainOfData = bodyInputStream;
                stream = streamFactory.wrap(bodyInputStream);
            } else {
                streamToDrainOfData = null;
                stream = bodyInputStream;
            }
        }
        return new UserFacingInputStream(stream, streamToDrainOfData);
    }

    private boolean isDeflatedStreamWrapped(InputStream inputStream) throws IOException {
        int b1 = inputStream.read() & 0xFF;
        int b2 = inputStream.read() & 0xFF;
        if (b1 == -1 || b2 == -1) {
            throw new ZipException("Deflated stream is too short");
        }
        int method = b1 & 0x0F;
        int info = b1 >> 4 & 0xF;
        return method == 8 && info <= 7 && (b1 << 8 | b2) % 31 == 0;
    }
}
