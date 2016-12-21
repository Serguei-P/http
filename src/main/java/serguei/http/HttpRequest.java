package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class HttpRequest {

    private final HttpRequestHeaders headers;

    private final HttpBody body;
    private final long contentLength;
    private final boolean chunked;

    HttpRequest(InputStream inputStream) throws IOException {
        this.headers = new HttpRequestHeaders(inputStream);
        contentLength = headers.getContentLength();
        chunked = contentLength < 0 && headers.hasChunkedBody();
        String encoding = headers.getHeader("content-encoding");
        body = new HttpBody(inputStream, contentLength, chunked, encoding);
    }

    public String getMethod() {
        return headers.getMethod();
    }

    public URL getUrl() {
        return headers.getUrl();
    }

    public String getVersion() {
        return headers.getVersion();
    }

    public String getHost() {
        return headers.getHost();
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

}
