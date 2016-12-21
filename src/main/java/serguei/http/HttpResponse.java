package serguei.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class HttpResponse {

    private static final String BODY_CODEPAGE = "UTF-8";
    private static final int BUFFER_SIZE = 1024 * 4;

    private final HttpResponseHeaders headers;
    private final long contentLength;
    private final boolean chunked;

    private InputStream responseBodyInputStream;

    HttpResponse(InputStream inputStream) throws IOException {
        this.headers = new HttpResponseHeaders(inputStream);
        contentLength = headers.getContentLength();
        chunked = contentLength < 0 && headers.hasChunkedBody();
        String encoding = headers.getHeader("content-encoding");
        responseBodyInputStream = inputStream;
        if (chunked) {
            responseBodyInputStream = new ChunkedInputStream(responseBodyInputStream);
        } else if (contentLength > 0) {
            responseBodyInputStream = new LimitedLengthInputStream(responseBodyInputStream, contentLength);
        }
        if (encoding != null && encoding.equals("gzip")) {
            responseBodyInputStream = new GZIPInputStream(responseBodyInputStream);
        }
    }

    public String getVersion() {
        return headers.getVersion();
    }

    public int getStatusCode() {
        return headers.getStatusCode();
    }

    public String getReason() {
        return headers.getReason();
    }

    public long getResponseContentLength() {
        return contentLength;
    }

    public boolean isResponseChunked() {
        return chunked;
    }

    public String getHeader(String headerName) {
        return headers.getHeader(headerName);
    }

    public List<String> getHeaders(String headerName) {
        return Collections.unmodifiableList(headers.getHeaders(headerName));
    }

    public String readResponseBodyAsString() throws IOException {
        byte[] buffer = readResponseBodyAsBytes();
        return new String(buffer, BODY_CODEPAGE);
    }

    public byte[] readResponseBodyAsBytes() throws IOException {
        if (contentLength > 0 || chunked) {
            return readStream(responseBodyInputStream);
        } else {
            return new byte[0];
        }
    }

    public String readResponseBodyAndUnzip() throws IOException {
        byte[] buffer = readResponseBodyAsBytes();
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
