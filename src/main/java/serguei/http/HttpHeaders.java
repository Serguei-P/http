package serguei.http;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * HTTP headers
 * 
 * @author Serguei Poliakov
 *
 */
abstract class HttpHeaders {

    private static final String LINE_SEPARATOR = "\r\n";
    static final byte[] LINE_SEPARATOR_BYTES = LINE_SEPARATOR.getBytes();
    protected static final byte[] SPACE = " ".getBytes();
    private static final String KEY_VALUE_SEPARATOR = ": ";
    private static final byte[] KEY_VALUE_SEPARATOR_BYTES = KEY_VALUE_SEPARATOR.getBytes();

    private final Map<String, HeaderValues> headers = new HashMap<>();

    /**
     * Read headers into the stream
     */
    protected void readHeaders(HeaderLineReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null && line.length() > 0) {
            addHeader(line);
        }
    }

    /**
     * This returns a header by name, if there are more then one header with this name, the first one will be returned
     */
    public String getHeader(String headerName) {
        headerName = normalize(headerName);
        HeaderValues values = headers.get(headerName);
        return values != null ? values.getValue() : null;
    }

    /**
     * This returns a headers by name, if there are more then one header with this name, all of them will be returned
     */
    public List<String> getHeaders(String headerName) {
        headerName = normalize(headerName);
        HeaderValues values = headers.get(headerName);
        return values != null ? values.getValues() : null;
    }

    /**
     * @return content length or -1 if content length is not defined
     */
    public long getContentLength() {
        String contentLengthString = getHeader("Content-Length");
        if (contentLengthString != null) {
            try {
                return Long.parseLong(contentLengthString.trim());
            } catch (NumberFormatException e) {
                // nothing
            }
        }
        return -1;
    }

    /**
     * @return true if the body is chunked or false if not
     */
    public boolean hasChunkedBody() {
        if (getContentLength() >= 0) {
            return false;
        }
        List<String> transferEncoding = getHeaders("Transfer-Encoding");
        if (transferEncoding != null) {
            int numberOfEncodings = transferEncoding.size();
            if (numberOfEncodings > 0) {
                String lastEncoding = transferEncoding.get(numberOfEncodings - 1);
                return lastEncoding.equals("chunked");
            }
        }
        return false;
    }

    /**
     * Adds a header, if header header with this name already exists, it adds a new entry without deleting existing
     */
    protected void addHeader(String line) throws HttpException {
        int index = line.indexOf(':');
        if (index > 0) {
            addHeader(line.substring(0, index).trim(), line.substring(index + 1).trim());
        } else {
            throw new HttpException("Colon (:) is missing from header");
        }
    }

    /**
     * Sets header If headers with this name already exists, replaces it
     */
    public void setHeader(String headerName, String value) {
        headers.put(normalize(headerName), new HeaderValues(headerName, value));
    }

    /**
     * Adds a header, if header header with this name already exists, it adds a new entry without deleting existing
     */
    public void addHeader(String headerName, String headerValue) {
        String normilizedHeaderName = normalize(headerName);
        HeaderValues values = headers.get(normilizedHeaderName);
        if (values == null) {
            headers.put(normilizedHeaderName, new HeaderValues(headerName, headerValue));
        } else {
            values.add(headerValue);
        }
    }

    /**
     * Removes header with a specified name
     */
    public void removeHeader(String headerName) {
        headers.remove(normalize(headerName));
    }

    /**
     * Write headers into the stream
     */
    protected void write(OutputStream output) throws IOException {
        for (Entry<String, HeaderValues> headerEntry : headers.entrySet()) {
            if (headerEntry.getValue().getValue() != null) {
                for (String header : headerEntry.getValue().getValues()) {
                    output.write(headerEntry.getValue().getName().getBytes());
                    output.write(KEY_VALUE_SEPARATOR_BYTES);
                    output.write(header.getBytes());
                    output.write(LINE_SEPARATOR_BYTES);
                }
            } else {
                output.write(headerEntry.getKey().getBytes());
                output.write(KEY_VALUE_SEPARATOR_BYTES);
                output.write(LINE_SEPARATOR_BYTES);
            }
        }
        output.write(LINE_SEPARATOR_BYTES);
        output.flush();
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase();
    }

    @Override
    public String toString() {
        boolean firstLine = true;
        StringBuilder builder = new StringBuilder();
        for (Entry<String, HeaderValues> headerEntry : headers.entrySet()) {
            if (headerEntry.getValue().getValue() != null) {
                for (String header : headerEntry.getValue().getValues()) {
                    if (!firstLine) {
                        builder.append(System.lineSeparator());
                    } else {
                        firstLine = false;
                    }
                    builder.append(headerEntry.getValue().getName());
                    builder.append(KEY_VALUE_SEPARATOR);
                    builder.append(header);
                }
            } else {
                if (!firstLine) {
                    builder.append(System.lineSeparator());
                } else {
                    firstLine = false;
                }
                builder.append(headerEntry.getKey());
                builder.append(KEY_VALUE_SEPARATOR);
            }
        }
        return builder.toString();
    }

    private static class HeaderValues {

        private final String name;
        private String value;
        private List<String> values;

        public HeaderValues(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void add(String value) {
            if (values == null) {
                values = new ArrayList<>();
                values.add(this.value);
                values.add(value);
            }
        }

        public String getValue() {
            if (values != null) {
                return values.get(0);
            } else {
                return value;
            }
        }

        public List<String> getValues() {
            if (values != null) {
                return values;
            } else {
                List<String> result = new ArrayList<>();
                if (value != null) {
                    result.add(value);
                }
                return result;
            }
        }
    }

    List<String> headerValues(String headerName) {
        List<String> result = new ArrayList<>();
        String headerValue = getHeader(headerName);
        if (headerValue == null) {
            return result;
        }
        char quote = ' ';
        boolean newValue = true;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < headerValue.length(); i++) {
            char ch = headerValue.charAt(i);
            if (quote != ' ') {
                if (ch == quote) {
                    quote = ' ';
                } else {
                    builder.append(ch);
                }
            } else {
                if (ch == ';') {
                    if (builder.length() > 0) {
                        result.add(builder.toString());
                        builder.setLength(0);
                        newValue = true;
                    }
                } else if (ch == '"') {
                    quote = ch;
                } else if (ch == ' ' && newValue) {
                } else {
                    newValue = false;
                    builder.append(ch);
                }
            }
        }
        if (builder.length() > 0) {
            result.add(builder.toString());
        }
        return result;
    }

    String getHeaderValue(String headerName, String valueName) {
        valueName += "=";
        List<String> result = headerValues(headerName);
        for (String value : result) {
            if (value.startsWith(valueName)) {
                return value.substring(valueName.length());
            }
        }
        return null;
    }
}
