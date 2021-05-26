package serguei.http;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    public static final long MAX_FILE_SIZE = -1;
    static final String LINE_SEPARATOR = "\r\n";
    static final byte[] LINE_SEPARATOR_BYTES = LINE_SEPARATOR.getBytes();
    protected static final byte[] SPACE = " ".getBytes();
    private static final String KEY_VALUE_SEPARATOR = ": ";
    private static final byte[] KEY_VALUE_SEPARATOR_BYTES = KEY_VALUE_SEPARATOR.getBytes();
    private static final int MAX_HEADER_NUMBER = 1000;
    private static final int UPPER_LOW_DIFF = 'a' - 'A';
    private static final BodyEncoding NO_BODY_ENCODING = new BodyEncoding(false, null);
    public static final String MAX_FILE_DOWNLOAD = "X-Max-File-Download";

    private final Map<String, HeaderValues> headers = new LinkedHashMap<>();

    protected HttpHeaders() {

    }

    protected HttpHeaders(HttpHeaders headers) {
        this.headers.putAll(headers.headers);
    }

    /**
     * Read headers into the stream
     */
    protected void readHeaders(HeaderLineReader reader) throws IOException {
        int number = 0;
        String line;
        while ((line = reader.readLine()) != null && line.length() > 0) {
            number++;
            if (number > MAX_HEADER_NUMBER) {
                throw new HttpException("Reading HTTP headers - too many headers found, max=" + MAX_HEADER_NUMBER);
            }
            addHeader(line);
        }
    }

    /**
     * This returns a header by name, if there are more then one header with this name, the first one will be returned
     * 
     * Returns null if header does not exit
     */
    public String getHeader(String headerName) {
        headerName = normalize(headerName);
        HeaderValues values = headers.get(headerName);
        return values != null ? values.getValue() : null;
    }

    /**
     * This returns headers by name, if there are more then one header with this name, all of them will be returned
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
        BodyEncoding bodyEncoding = getBodyEncoding();
        return bodyEncoding.isChunked;
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
        String normalizedHeaderName = normalize(headerName);
        HeaderValues values = headers.get(normalizedHeaderName);
        if (values == null) {
            headers.put(normalizedHeaderName, new HeaderValues(headerName, headerValue));
        } else {
            headers.put(normalizedHeaderName, values.addValue(headerValue));
        }
    }

    /**
     * Removes header with a specified name
     */
    public void removeHeader(String headerName) {
        headers.remove(normalize(headerName));
    }

    /**
     * @return List of header names
     */
    public List<String> listHeaderNames() {
        return new ArrayList<>(headers.keySet());
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

    boolean isEmpty() {
        return headers.isEmpty();
    }

    private static String normalize(String name) {
        name = name.trim();
        if (isNormalized(name)) {
            return name;
        }
        StringBuilder builder = new StringBuilder(name.length());
        boolean upper = true;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '-') {
                builder.append(ch);
                upper = true;
            } else {
                if (upper) {
                    if (ch >= 'a' && ch <= 'z') {
                        builder.append((char)(ch - UPPER_LOW_DIFF));
                    } else {
                        builder.append(ch);
                    }
                    upper = false;
                } else {
                    if (ch >= 'A' && ch <= 'Z') {
                        builder.append((char)(ch + UPPER_LOW_DIFF));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static boolean isNormalized(String name) {
        boolean expectUpper = true;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '-')  {
                expectUpper = true;
            } else {
                if (expectUpper) {
                    if (ch >= 'a' && ch <= 'z') {
                        return false;
                    }
                    expectUpper = false;
                } else {
                    if (ch >= 'A' && ch <= 'Z') {
                        return false;
                    }
                }
            }
        }
        return true;
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
        private final String value;
        private final List<String> values;

        private HeaderValues(String name, String value) {
            this.name = name;
            this.value = value;
            this.values = null;
        }

        private HeaderValues(String name, List<String> values) {
            this.name = name;
            this.value = null;
            this.values = values;
        }

        private String getName() {
            return name;
        }

        private HeaderValues addValue(String value) {
            if (this.values == null) {
                List<String> newValues = new ArrayList<>();
                newValues.add(this.value);
                newValues.add(value);
                return new HeaderValues(name, newValues);
            } else {
                List<String> newValues = new ArrayList<>(values);
                newValues.add(value);
                return new HeaderValues(name, newValues);
            }
        }

        private String getValue() {
            if (values != null) {
                return values.get(0);
            } else {
                return value;
            }
        }

        private List<String> getValues() {
            if (values != null) {
                return values;
            } else {
                if (value != null) {
                    List<String> result = new ArrayList<>();
                    result.add(value);
                    return result;
                } else {
                    return Collections.emptyList();
                }
            }
        }

        @Override
        public String toString() {
            if (values != null) {
                return name + " - " + values;
            } else {
                return name + " - " + value;
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

    long getAllowedContentLength() {
        HeaderValues header = headers.get(MAX_FILE_DOWNLOAD);
        if (header == null) {
            return MAX_FILE_SIZE;
        }
        return Long.valueOf(header.getValue());
    }
    BodyEncoding getBodyEncoding() {
        List<String> list = encodingData();
        if (list.size() == 0) {
            return NO_BODY_ENCODING;
        }
        boolean isChunked = false;
        String encoding = null;
        int lastIndex = list.size() - 1;
        String lastEncoding = list.get(lastIndex);
        if (lastEncoding.equalsIgnoreCase("chunked")) {
            isChunked = true;
            lastIndex--;
            if (lastIndex >= 0) {
                encoding = list.get(lastIndex);
            }
        } else {
            encoding = lastEncoding;
        }
        return new BodyEncoding(isChunked, encoding);
    }

    List<String> encodingData() {
        List<String> result = null;
        HeaderValues header = headers.get("Content-Encoding");
        if (header != null) {
            result = new ArrayList<>();
            result.add(header.getValue());
        }
        header = headers.get("Transfer-Encoding");
        if (header != null) {
            if (result == null) {
                result = new ArrayList<>();
            }
            if (header.values != null) {
                for (String value : header.values) {
                    addCommaDelimitedValues(value, result);
                }
            } else {
                addCommaDelimitedValues(header.value, result);
            }
        }
        if (result != null) {
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    private void addCommaDelimitedValues(String line, List<String> list) {
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == ' ' || ch == ',') {
                if (i > start) {
                    list.add(line.substring(start, i));
                }
                start = i + 1;
            }
        }
        if (start < line.length()) {
            list.add(line.substring(start));
        }
    }

    static class BodyEncoding {

        private boolean isChunked;
        private String encoding;

        private BodyEncoding(boolean isChunked, String encoding) {
            this.isChunked = isChunked;
            this.encoding = encoding;
        }

        public boolean isChunked() {
            return isChunked;
        }

        public String geEncoding() {
            return encoding;
        }
    }

}
