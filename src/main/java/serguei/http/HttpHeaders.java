package serguei.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class HttpHeaders {
    
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

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

    private static String normalize(String name) {
        return name.trim().toLowerCase();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (Entry<String, HeaderValues> headerEntry : headers.entrySet()) {
            if (headerEntry.getValue().getValue() != null) {
                for (String header : headerEntry.getValue().getValues()) {
                    if (!isFirst) {
                        sb.append(LINE_SEPARATOR);
                    }
                    sb.append(headerEntry.getKey());
                    sb.append(": ");
                    sb.append(header);
                    isFirst = false;
                }
            } else {
                if (!isFirst) {
                    sb.append(LINE_SEPARATOR);
                }
                sb.append(headerEntry.getKey());
                sb.append(": null");
                isFirst = false;
            }
        }
        return sb.toString();
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
