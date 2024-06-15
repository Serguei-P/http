package serguei.http;

import serguei.http.utils.EmptyInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * 
 * This represents a request as received by a server
 * 
 * @author Serguei Poliakov
 *
 */
public class HttpRequest {

    private static final HttpBody EMPTY_BODY = new HttpBody(new EmptyInputStream(), 0, false, null, false);

    private final HttpRequestHeaders headers;
    private final HttpBody body;
    private final long contentLength;
    private final boolean chunked;
    private final URL url;

    public HttpRequest(InputStream inputStream) throws IOException {
        this(new HttpRequestHeaders(inputStream), inputStream);
    }

    HttpRequest(HttpRequestHeaders requestHeaders, InputStream inputStream) throws IOException {
        this.headers = requestHeaders;
        this.url = headers.getUrl();
        String method = headers.getMethod();
        if (!method.equals("GET") && !method.equals("CONNECT")) {
            HttpHeaders.BodyEncoding bodyEncoding = headers.getBodyEncoding();
            contentLength = headers.getContentLength();
            chunked = contentLength < 0 && bodyEncoding.isChunked();
            body = new HttpBody(inputStream, contentLength, chunked, bodyEncoding.geEncoding(), false);
        } else {
            contentLength = 0;
            chunked = false;
            body = EMPTY_BODY;
        }
    }

    /**
     * @return HTTP method (e.g. GET, POST etc)
     */
    public String getMethod() {
        return headers.getMethod();
    }

    /**
     * @return Request url (build from a request line and, if it is missing host name, from Host header)
     */
    public URL getUrl() {
        return url;
    }

    /**
     * @return HTTP version ("HTTP/1.0" or "HTTP/1.1")
     */
    public String getVersion() {
        return headers.getVersion();
    }

    /**
     * @return Host name. Comes from Host header or, if absent (e.g. when HTTP/1.0) then from request line
     */
    public String getHost() {
        String host = headers.getHeader("Host");
        if (host != null) {
            return host;
        } else {
            return url.getHost();
        }
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
     * @return true if it is multi-part request
     */
    public boolean hasMultipartBody() {
        String contentType = headers.getHeader("Content-Type");
        if (contentType != null) {
            return contentType.startsWith("multipart/") && body != null;
        } else {
            return false;
        }
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
     * This reads the body of the request and parses it assuming it contains HTML Form data
     * 
     * @throws IOException
     */
    public RequestValues readBodyAsValues() throws IOException {
        if (hasMultipartBody()) {
            String boundary = headers.getHeaderValue("Content-Type", "boundary");
            if (boundary == null) {
                throw new HttpException("Boundary not specified in Content-Type header for multi-part request");
            }
            MultipartBodyParser multipartBodyParser = new MultipartBodyParser(body.getBodyInputStream(), boundary);
            return new RequestValues(multipartBodyParser);
        } else {
            return new RequestValues(body.readAsString());
        }
    }

    /**
     * This returns the body of the request as a stream
     * 
     * Please note it is the user needs to close this stream if connection is to be reused
     * 
     * @throws IOException
     *             - thrown if decompression fails
     */
    public InputStream getBodyAsStream() throws IOException {
        return body.getBodyInputStream();
    }

    /**
     * This returns the body of the request before decompression is done to the stream (please note that un-chunking
     * still takes place)
     * 
     * Please note it is the user needs to close this stream if connection is to be reused
     * 
     * Please also note that use of getBodyAsString() and getBodyAsOriginalStream() are incompatible. The first call
     * decides if it is original or decompressed stream
     */
    public InputStream getBodyAsOriginalStream() {
        return body.getOriginalBodyInputStream();
    }

    /**
     * @return copy of the HTTP headers
     */
    public HttpRequestHeaders getHeaders() {
        return new HttpRequestHeaders(headers);
    }

    /**
     * @return true if response has a body
     */
    public boolean hasBody() {
        return body.hasBody();
    }

    /**
     * @return true if request has a body and the body is compressed
     */
    public boolean isBodyCompressed() {
        return body.isCompressed();
    }

    /**
     * @return HTTP headers (without copying)
     */
    HttpRequestHeaders headers() {
        return headers;
    }

    /**
     * This reads what left of the request body, so that the connection is ready to read a next request
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
