package serguei.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

class HttpBody {

    static final String BODY_CODEPAGE = "UTF-8";
    private static final int BUFFER_SIZE = 1024 * 4;

    private final InputStream bodyInputStream;
    private final boolean hasBody;

    HttpBody(InputStream inputStream, long contentLength, boolean chunked, String encoding) throws IOException {
        InputStream stream = inputStream;
        if (chunked) {
            stream = new ChunkedInputStream(inputStream);
        } else if (contentLength > 0) {
            stream = new LimitedLengthInputStream(inputStream, contentLength);
        }
        if (encoding != null && encoding.equals("gzip")) {
            stream = new GZIPInputStream(stream);
        }
        this.bodyInputStream = stream;
        this.hasBody = contentLength > 0 || chunked;
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

    String readAndUnzipAsString() throws IOException {
        byte[] buffer = readAsBytes();
        ByteArrayInputStream zippedInputStream = new ByteArrayInputStream(buffer);
        GZIPInputStream stream = new GZIPInputStream(zippedInputStream);
        byte[] result = readStream(stream);
        return new String(result, BODY_CODEPAGE);
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

}
