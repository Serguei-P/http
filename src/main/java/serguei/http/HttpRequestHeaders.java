package serguei.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * This represents request headers - combination of the the request line and the following headers as specified in
 * RFC-2616: https://tools.ietf.org/html/rfc2616
 * 
 * This class is mutable and it is not thread safe. One would directly create and manipulate instance of this class to
 * prepare request headers in the client
 * 
 * @author Serguei Poliakov
 * 
 */
public final class HttpRequestHeaders extends HttpHeaders {

    private static final String PROTOCOL_SEPARATOR = "://";

    private String method;
    private String version;
    private String path;

    /**
     * This creates an instance of HttpRequestHeaders
     * 
     * @param requestLine
     *            - request line, e.g. "GET / HTTP/1.1"
     * @param headers
     *            - headers in the form "Host: www.google.co.uk"
     * @throws HttpException
     *             - can be thrown when request line or headers do not follow HTTP standards
     */
    public HttpRequestHeaders(String requestLine, String... headers) throws HttpException {
        parseRequestLine(requestLine);
        for (String header : headers) {
            addHeader(header);
        }
    }

    /**
     * This creates an instance of this class by reading request line and headers from a stream
     * 
     * @param inputStream
     * @throws IOException
     *             - thrown when the data is not HTTP or IO errors
     */
    public HttpRequestHeaders(InputStream inputStream) throws IOException {
        HeaderLineReader reader = new HeaderLineReader(inputStream);
        String line = reader.readLine();
        if (line != null) {
            parseRequestLine(line);
        } else {
            throw new HttpException("Unexpected EOF when reading HTTP message");
        }
        readHeaders(reader);
    }

    HttpRequestHeaders(HttpRequestHeaders requestHeaders) {
        super(requestHeaders);
        this.method = requestHeaders.method;
        this.version = requestHeaders.version;
        this.path = requestHeaders.path;
    }

    /**
     * This returns Url based on command line and host header. If request line contains host, it has a priority over
     * what is specified in host header
     * 
     * @throws HttpException
     *             - thrown when URL is incorrect
     */
    public URL getUrl() throws HttpException {
        String host = getHeader("Host");
        return parseUrl(path, host);
    }

    /**
     * This returns host from Host header or, if Host header does not exist (e.g. when HTTP/1.0) then from path in
     * command line
     * 
     * @throws HttpException
     *             - thrown if there was an error parsing path in request line
     */
    public String getHost() throws HttpException {
        String host = getHeader("Host");
        if (host != null) {
            return host;
        } else {
            return parseUrl(path, null).getHost();
        }
    }

    /**
     * This writes the request line and headers into the output stream
     * 
     * This includes an empty line separating headers and body (i.e. you can start writing the body immediately after
     * this)
     */
    @Override
    public void write(OutputStream outputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(method.getBytes());
        out.write(SPACE);
        out.write(path.getBytes());
        out.write(SPACE);
        out.write(version.getBytes());
        out.write(LINE_SEPARATOR_BYTES);
        super.write(out);
        outputStream.write(out.toByteArray());
    }

    /**
     * @return request method (e.g. "GET", "POST")
     */
    public String getMethod() {
        return method;
    }

    /**
     * @return HTTP version (e.g. "HTTP/1.1")
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return Path as specified in Request Line
     */
    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return method + " " + path + " " + version + System.lineSeparator() + super.toString();
    }

    static String getMethodFromRequestLine(String requestLine) {
        int pos = 0;
        while (pos <= requestLine.length() && requestLine.charAt(pos) != ' ') {
            pos++;
        }
        return requestLine.substring(0, pos);
    }

    static String getMethodFromRequest(byte[] data) {
        int pos = 0;
        while (pos <= data.length && data[pos] != ' ') {
            pos++;
        }
        return new String(Arrays.copyOf(data, pos), StandardCharsets.UTF_8);

    }

    private void parseRequestLine(String requestLine) throws HttpException {
        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new HttpException("Wrong number of elements in command line: " + requestLine);
        }
        method = parts[0];
        path = parts[1];
        version = parts[2];
    }

    private URL parseUrl(String line, String host) throws HttpException {
        String protocol;
        String fullPath;
        int pos = line.indexOf(PROTOCOL_SEPARATOR);
        if (pos > 0) {
            protocol = line.substring(0, pos);
            fullPath = line.substring(pos + PROTOCOL_SEPARATOR.length());
        } else {
            protocol = "http";
            fullPath = line;
        }
        String path;
        if (fullPath.startsWith("/")) {
            path = fullPath;
        } else {
            pos = fullPath.indexOf('/');
            if (pos > 0) {
                host = fullPath.substring(0, pos);
                path = fullPath.substring(pos);
            } else {
                host = fullPath;
                path = "";
            }
        }
        if (host == null || host.length() == 0) {
            throw new HttpException("No host found in request headers");
        }
        try {
            return new URL(protocol, host, path);
        } catch (MalformedURLException e) {
            throw new HttpException("Cannot create url for protocol: " + protocol + ", host: " + host + ", path: " + path);
        }
    }

    /**
     * Creates request headers for CONNECT request
     */
    public static HttpRequestHeaders connectRequest(String host) {
        try {
            return new HttpRequestHeaders("CONNECT " + host + " HTTP/1.1", "Host: " + host);
        } catch (HttpException e) {
            // should never happen
            return null;
        }
    }

    /**
     * Creates request headers for GET request
     */
    public static HttpRequestHeaders getRequest(String url) throws IOException {
        String host = (new URL(url)).getHost();
        return new HttpRequestHeaders("GET " + url + " HTTP/1.1", "Host: " + host);
    }

    /**
     * Creates request headers for POST request
     */
    public static HttpRequestHeaders postRequest(String url) throws IOException {
        String host = (new URL(url)).getHost();
        return new HttpRequestHeaders("POST " + url + " HTTP/1.1", "Host: " + host);
    }

}
