package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class HttpRequestHeaders extends HttpHeaders {

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private String method;
    private String version;
    private URL url;

    public HttpRequestHeaders(InputStream inputStream) throws IOException {
        HeaderLineReader reader = new HeaderLineReader(inputStream);
        String line = reader.readLine();
        if (line != null) {
            parseCommandLine(line);
        } else {
            throw new HttpException("Unexpected EOF when reading HTTP message");
        }
        readHeaders(reader);
    }

    public HttpRequestHeaders(String commandLine, String... headers) throws HttpException {
        parseCommandLine(commandLine);
        for (String header : headers) {
            addHeader(header);
        }
    }

    @Override
    public void write(OutputStream output) throws IOException {
        output.write(method.getBytes());
        output.write(SPACE);
        output.write(url.toString().getBytes());
        output.write(SPACE);
        output.write(version.getBytes());
        output.write(LINE_SEPARATOR_BYTES);
        super.write(output);
    }

    public String getMethod() {
        return method;
    }

    public URL getUrl() {
        return url;
    }

    public String getVersion() {
        return version;
    }

    public String getHost() {
        String host = getHeader("host");
        if (host != null) {
            return host;
        } else {
            return url.getHost();
        }
    }

    @Override
    public String toString() {
        return method + " " + url + " " + version + LINE_SEPARATOR + super.toString();
    }

    private void parseCommandLine(String commandLine) throws HttpException {
        String[] parts = commandLine.split(" ");
        if (parts.length != 3) {
            throw new HttpException("Wrong number of elements in command line: " + commandLine);
        }
        method = parts[0];
        try {
            url = new URL(parts[1]);
        } catch (MalformedURLException e) {
            throw new HttpException("Malformed URL element: " + parts[1]);
        }
        version = parts[2];
    }

}
