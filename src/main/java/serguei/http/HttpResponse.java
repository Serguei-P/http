package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * 
 * This represents a response as received by a client
 * 
 * @author Serguei Poliakov
 *
 */
public class HttpResponse {

    private final HttpResponseHeaders headers;
    private final HttpBody body;
    private final long contentLength;
    private final boolean chunked;

    HttpResponse(InputStream inputStream) throws IOException {
        this.headers = new HttpResponseHeaders(inputStream);
        contentLength = headers.getContentLength();
        chunked = contentLength < 0 && headers.hasChunkedBody();
        String encoding = headers.getHeader("content-encoding");
        body = new HttpBody(inputStream, contentLength, chunked, encoding);
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

    public long getContentLength() {
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

    public String readBodyAsString() throws IOException {
        return body.readAsString();
    }

    public byte[] readBodyAsBytes() throws IOException {
        return body.readAsBytes();
    }

    public String readBodyAndUnzip() throws IOException {
        return body.readAndUnzipAsString();
    }

    @Override
    public String toString() {
        return headers.toString();
    }

}
