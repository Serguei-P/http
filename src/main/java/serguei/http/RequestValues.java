package serguei.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class RequestValues {

    private final Map<String, Object> values = new HashMap<>();

    RequestValues(String requestBody) {
        String[] nameAndValuePairs = requestBody.split("&");
        for (String nameAndValue : nameAndValuePairs) {
            int pos = nameAndValue.indexOf('=');
            if (pos > 0) {
                String name = nameAndValue.substring(0, pos);
                String value = decode(nameAndValue.substring(pos + 1));
                values.put(name, value);
            }
        }
    }

    RequestValues(MultipartBodyParser multipartBodyParser) throws IOException {
        BodyPart bodyPart;
        while ((bodyPart = multipartBodyParser.readNextBodyPart()) != null) {
            if (bodyPart.getName() != null) {
                FileValue value = new FileValue(bodyPart.getContentType(), bodyPart.getFilename(),
                        bodyPart.getContentAsBytes());
                values.put(bodyPart.getName(), value);
            }
        }
    }

    public String getValue(String name) {
        Object value = values.get(name);
        if (value != null) {
            return value.toString();
        } else {
            return null;
        }
    }

    public byte[] getBytesValue(String name) {
        Object value = values.get(name);
        if (value != null) {
            if (value instanceof FileValue) {
                return ((FileValue)value).data;
            } else {
                return HttpBody.stringAsBytes(value.toString());
            }
        } else {
            return null;
        }
    }

    public String getContentType(String name) {
        Object value = values.get(name);
        if (value != null) {
            if (value instanceof FileValue) {
                return ((FileValue)value).contentType;
            } else {
                return "text/plain";
            }
        } else {
            return null;
        }
    }

    public String getFileName(String name) {
        Object value = values.get(name);
        if (value != null) {
            if (value instanceof FileValue) {
                return ((FileValue)value).fileName;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, HttpBody.BODY_CODEPAGE);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Character set " + HttpBody.BODY_CODEPAGE + " is not supported");
        }
    }

    private static class FileValue {

        private final String contentType;
        private final String fileName;
        private final byte[] data;

        private FileValue(String contentType, String fileName, byte[] data) {
            this.contentType = contentType;
            this.fileName = fileName;
            this.data = data;
        }

        @Override
        public String toString() {
            return HttpBody.bytesAsString(data);
        }
    }
}
