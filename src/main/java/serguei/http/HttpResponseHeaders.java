package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This represents response headers - combination of the the status line and the following headers as specified in
 * RFC-2616: https://tools.ietf.org/html/rfc2616
 * 
 * This class is mutable and it is not thread safe. One would directly create and manipulate instance of this class to
 * prepare response headers in the server
 * 
 * @author Serguei Poliakov
 * 
 */
public final class HttpResponseHeaders extends HttpHeaders {

    private String version;
    private int statusCode;
    private String reason;

    /**
     * This creates an instance of HttpResponseHeaders
     * 
     * @param statusLine
     *            - status line, e.g. "GET / HTTP/1."
     * @param headers
     *            - headers in the form "Content-Length: 100"
     * @throws HttpException
     *             - can be thrown when status line or headers do not follow HTTP standards
     */
    public HttpResponseHeaders(String statusLine, String... headers) throws HttpException {
        parseResponseLine(statusLine);
        for (String header : headers) {
            addHeader(header);
        }
    }

    /**
     * This creates an instance of this class by reading status line and headers from a stream
     * 
     * @param inputStream
     * @throws IOException
     *             - thrown when the data is not HTTP or IO errors
     */
    HttpResponseHeaders(InputStream inputStream) throws IOException {
        HeaderLineReader reader = new HeaderLineReader(inputStream);
        String line = reader.readLine();
        if (line != null && line.length() == 0) {
            line = reader.readLine();
        }
        if (line != null) {
            parseResponseLine(line);
        } else {
            throw new HttpException("Unexpected EOF when reading HTTP message");
        }
        readHeaders(reader);
    }

    HttpResponseHeaders(HttpResponseHeaders responseHeaders) {
        super(responseHeaders);
        this.version = responseHeaders.version;
        this.statusCode = responseHeaders.statusCode;
        this.reason = responseHeaders.reason;
    }

    public static HttpResponseHeaders ok() {
        try {
            return new HttpResponseHeaders("HTTP/1.1 200 OK");
        } catch (HttpException e) {
            // can never happen
            return null;
        }
    }

    public static HttpResponseHeaders redirectTo(String location) {
        try {
            return new HttpResponseHeaders("HTTP/1.1 302 Found", "Location: " + location);
        } catch (HttpException e) {
            // can never happen
            return null;
        }
    }

    public static HttpResponseHeaders notFound() {
        try {
            return new HttpResponseHeaders("HTTP/1.1 404 Not Found");
        } catch (HttpException e) {
            // can never happen
            return null;
        }
    }

    public static HttpResponseHeaders serverError() {
        try {
            return new HttpResponseHeaders("HTTP/1.1 500 Server Error");
        } catch (HttpException e) {
            // can never happen
            return null;
        }
    }

    @Override
    public void write(OutputStream output) throws IOException {
        output.write(version.getBytes());
        output.write(SPACE);
        output.write(Integer.toString(statusCode).getBytes());
        output.write(SPACE);
        output.write(reason.getBytes());
        output.write(LINE_SEPARATOR_BYTES);
        super.write(output);
    }

    @Override
    public String toString() {
        return version + " " + statusCode + " " + reason + System.lineSeparator() + super.toString();
    }

    public String getVersion() {
        return version;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReason() {
        return reason;
    }

    private final void parseResponseLine(String line) throws HttpException {
        int versionEndPos = line.indexOf(' ');
        if (versionEndPos > 0) {
            version = line.substring(0, versionEndPos);
        } else {
            throwWrongNumberOfElementsException(line);
        }
        int statusEndPos = line.indexOf(' ', versionEndPos + 1);
        if (statusEndPos > 0) {
            String statusCodeAsString = line.substring(versionEndPos + 1, statusEndPos);
            try {
                statusCode = Integer.parseInt(statusCodeAsString);
            } catch (NumberFormatException e) {
                throw new HttpException("Cannot parse status code " + statusCodeAsString);
            }
        } else {
            throwWrongNumberOfElementsException(line);
        }
        reason = line.substring(statusEndPos + 1);
    }

    private void throwWrongNumberOfElementsException(String line) throws HttpException {
        throw new HttpException("Wrong number of elements in response line: \"" + line + "\"");
    }

}
