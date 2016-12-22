package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class HttpRequestHeaders extends HttpHeaders {

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String PROTOCOL_SEPARATOR = "://";

    private String method;
    private String version;
    private String path;

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

    public URL getUrl() throws HttpException {
        String host = getHeader("Host");
        return parseUrl(path, host);
    }

    public String getHost() throws HttpException {
        String host = getHeader("Host");
        if (host != null) {
            return host;
        } else {
            return parseUrl(path, null).getHost();
        }
    }

    @Override
    public void write(OutputStream output) throws IOException {
        output.write(method.getBytes());
        output.write(SPACE);
        output.write(path.getBytes());
        output.write(SPACE);
        output.write(version.getBytes());
        output.write(LINE_SEPARATOR_BYTES);
        super.write(output);
    }

    public String getMethod() {
        return method;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return method + " " + path + " " + version + LINE_SEPARATOR + super.toString();
    }

    private void parseCommandLine(String commandLine) throws HttpException {
        String[] parts = commandLine.split(" ");
        if (parts.length != 3) {
            throw new HttpException("Wrong number of elements in command line: " + commandLine);
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

}
