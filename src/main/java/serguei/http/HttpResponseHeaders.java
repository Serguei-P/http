package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HttpResponseHeaders extends HttpHeaders {

    private String version;
    private int statusCode;
    private String reason;

    public HttpResponseHeaders(InputStream inputStream) throws IOException {
        HeaderLineReader reader = new HeaderLineReader(inputStream);
        String line = reader.readLine();
        if (line != null) {
            parseResponseLine(line);
        } else {
            throw new HttpException("Unexpected EOF when reading HTTP message");
        }
        readHeaders(reader);
    }

    public HttpResponseHeaders(String responseLine, String... headers) throws HttpException {
        parseResponseLine(responseLine);
        for (String header : headers) {
            addHeader(header);
        }
    }

    public static HttpResponseHeaders ok() {
        try {
            return new HttpResponseHeaders("HTTP/1.1 200 OK");
        } catch (HttpException e) {
            // can never happen
            return null;
        }
    }

    public static HttpResponseHeaders redirect(String location) {
        try {
            return new HttpResponseHeaders("HTTP/1.1 302 Found", "Location: " + location);
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

    public String getVersion() {
        return version;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReason() {
        return reason;
    }

    private void parseResponseLine(String line) throws HttpException {
        int versionPos = line.indexOf(' ');
        if (versionPos > 0) {
            version = line.substring(0, versionPos);
        } else {
            throwWrongNumberOfElementsException(line);
        }
        int statusPos = line.indexOf(' ', versionPos + 1);
        if (statusPos > 0) {
            String statusCodeAsString = line.substring(versionPos + 1, statusPos);
            try {
                statusCode = Integer.parseInt(statusCodeAsString);
            } catch (NumberFormatException e) {
                throw new HttpException("Cannot parse status code " + statusCodeAsString);
            }
        } else {
            throwWrongNumberOfElementsException(line);
        }
        reason = line.substring(statusPos + 1);
    }

    private void throwWrongNumberOfElementsException(String line) throws HttpException {
        throw new HttpException("Wrong number of elements in response line: " + line);
    }

}
