package serguei.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class HttpHeaders {
    
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    static final byte[] LINE_SEPARATOR_BYTES = LINE_SEPARATOR.getBytes();
    protected static final byte[] SPACE = " ".getBytes();
    private static final byte[] KEY_VALUE_SEPARATOR = ": ".getBytes();

    private Map<String, HeaderValues> headers = new HashMap<>();
    
    protected void readHeaders(HeaderLineReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null && line.length() > 0) {
            addHeader(line);
        }
    }

    public String getHeader(String headerName) {
        headerName = normalize(headerName);
        HeaderValues values = headers.get(headerName);
        return values != null ? values.getValue() : null;
    }

    public List<String> getHeaders(String headerName) {
        headerName = normalize(headerName);
        HeaderValues values = headers.get(headerName);
        return values != null ? values.getValues() : null;
    }

    public void setHeader(String headerName, String value) {
        headerName = normalize(headerName);
        headers.put(headerName, new HeaderValues(value));
    }

    public void removeHeader(String headerName) {
        headerName = normalize(headerName);
        headers.remove(headerName);
    }

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

    protected void addHeader(String line) throws HttpException {
        int index = line.indexOf(':');
        if (index > 0) {
            addHeader(line.substring(0, index).trim(), line.substring(index + 1).trim());
        } else {
            throw new HttpException("Colon (:) is missing from header");
        }
    }

    protected void addHeader(String headerName, String headerValue) {
        headerName = normalize(headerName);
        HeaderValues values = headers.get(headerName);
        if (values == null) {
            headers.put(headerName, new HeaderValues(headerValue));
        } else {
            values.add(headerValue);
        }
    }

    protected void write(OutputStream output) throws IOException {
        for (Entry<String, HeaderValues> headerEntry : headers.entrySet()) {
            if (headerEntry.getValue().getValue() != null) {
                for (String header : headerEntry.getValue().getValues()) {
                    output.write(headerEntry.getKey().getBytes());
                    output.write(KEY_VALUE_SEPARATOR);
                    output.write(header.getBytes());
                    output.write(LINE_SEPARATOR_BYTES);
                }
            } else {
                output.write(headerEntry.getKey().getBytes());
                output.write(KEY_VALUE_SEPARATOR);
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
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            write(output);
        } catch (IOException e) {
            // nothing
        }
        return output.toString();
    }

    private static class HeaderValues {
        private String value;
        private List<String> values;

        public HeaderValues(String value) {
            this.value = value;
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

}
