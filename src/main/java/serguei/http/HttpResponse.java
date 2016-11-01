package serguei.http;

import java.io.IOException;
import java.io.InputStream;

public class HttpResponse extends HttpHeaders {

    private String version;
    private int statusCode;
    private String reason;

    public HttpResponse(InputStream inputStream) throws IOException {
        HeaderLineReader reader = new HeaderLineReader(inputStream);
        String line = reader.readLine();
        if (line != null) {
            parseResponseLine(line);
        } else {
            throw new HttpException("Unexpected EOF when reading HTTP message");
        }
        readHeaders(reader);
    }

    public HttpResponse(String responseLine, String... headers) throws HttpException {
        parseResponseLine(responseLine);
        for (String header : headers) {
            addHeader(header);
        }
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
        String[] parts = line.split(" ");
        if (parts.length != 3) {
            throw new HttpException("Wrong number of elements in response line: " + line);
        }
        version = parts[0];
        try {
            statusCode = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new HttpException("Cannot parse status code " + parts[1]);
        }
        reason = parts[2];
    }

}
