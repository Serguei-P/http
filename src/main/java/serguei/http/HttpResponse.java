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

    public HttpResponse(InputStream inputStream) throws IOException {
        this.headers = new HttpResponseHeaders(inputStream);
        contentLength = headers.getContentLength();
        HttpHeaders.BodyEncoding bodyEncoding = headers.getBodyEncoding();
        chunked = contentLength < 0 && bodyEncoding.isChunked();
        body = new HttpBody(inputStream, contentLength, chunked, bodyEncoding.geEncoding(), true);
    }

    /**
     * @return HTTP version ("HTTP/1.0" or "HTTP/1.1")
     */
    public String getVersion() {
        return headers.getVersion();
    }

    /**
     * @return HTTP status code (e.g. 200 = OK or 404 = not found)
     */
    public int getStatusCode() {
        return headers.getStatusCode();
    }

    /**
     * @return Description sent with HTTP status code
     */
    public String getReason() {
        return headers.getReason();
    }

    /**
     * @return content length (as set in Content-Length header). If absent - returns -1
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * @return true if body is sent using chunked encoding
     */
    public boolean isContentChunked() {
        return chunked;
    }

    /**
     * This returns an HTTP header by name, if there are more then one header with this name, the first one will be
     * returned, if header with this name does not exist, null is returned. The name is not case-sensitive.
     */
    public String getHeader(String headerName) {
        return headers.getHeader(headerName);
    }

    /**
     * This returns headers by name, if there are more then one header with this name, all of them will be returned, if
     * headers with this name don't exist, an empty list is returned. The name is not case-sensitive.
     */
    public List<String> getHeaders(String headerName) {
        List<String> list = headers.getHeaders(headerName);
        if (list != null) {
            return Collections.unmodifiableList(list);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * This reads the body of the request and returns it as a string
     * 
     * @throws IOException
     */
    public String readBodyAsString() throws IOException {
        return body.readAsString();
    }

    /**
     * This reads the body of the request and returns it as an array of bytes
     * 
     * @throws IOException
     */
    public byte[] readBodyAsBytes() throws IOException {
        return body.readAsBytes();
    }

    /**
     * This returns the body of the response as a stream
     * 
     * Please note it is the user needs to close this stream if connection is to be reused
     */
    public InputStream getBodyAsStream() {
        return body.getBodyInputStream();
    }

    /**
     * @return true if response has a body
     */
    public boolean hasBody() {
        if (body != null) {
            return body.hasBody();
        } else {
            return false;
        }
    }

    /**
     * @return true if response has a body and the body is compressed
     */
    public boolean isBodyCompressed() {
        if (body != null) {
            return body.isCompressed();
        } else {
            return false;
        }
    }

    /**
     * @return HTTP headers
     */
    public HttpResponseHeaders getHeaders() {
        return new HttpResponseHeaders(headers);
    }

    /**
     * This reads what left of the response body, so that the connection is ready to read a next request
     * 
     * @throws IOException
     */
    public void drainBody() throws IOException {
        if (body != null) {
            body.drain();
        }
    }

    @Override
    public String toString() {
        return headers.toString();
    }

}
