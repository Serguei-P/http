package serguei.http;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class MultipartFormDataRequestBody {

    private final byte[] END = "--".getBytes();

    private final byte[] border;

    private final List<byte[]> dataList = new ArrayList<>();

    public MultipartFormDataRequestBody(String border) {
        this.border = ("--" + border).getBytes();
    }

    public void add(byte[] data, String name) {
        add(data, name, null, null);
    }

    public void add(String data, String name) {
        add(data, name, null, null);
    }

    public void add(String data, String name, String fileName, String contentType) {
        try {
            add(data.getBytes(HttpBody.BODY_CODEPAGE), name, fileName, contentType);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("String to bytes conversion error", e);
        }
    }

    public void add(byte[] data, String name, String fileName, String contentType) {
        dataList.add(this.border);
        dataList.add(HttpHeaders.LINE_SEPARATOR_BYTES);
        dataList.add(buildHeaders(name, fileName, contentType));
        dataList.add(data);
        dataList.add(HttpHeaders.LINE_SEPARATOR_BYTES);
    }

    public byte[] getBody() {
        int len = 0;
        for (byte[] data : dataList) {
            len += data.length;
        }
        len += border.length;
        len += END.length;
        len += HttpHeaders.LINE_SEPARATOR_BYTES.length;
        ResultBuilder result = new ResultBuilder(len);
        for (byte[] data : dataList) {
            result.add(data);
        }
        result.add(border);
        result.add(END);
        result.add(HttpHeaders.LINE_SEPARATOR_BYTES);
        return result.result;
    }

    private byte[] buildHeaders(String name, String fileName, String contentType) {
        StringBuilder builder = new StringBuilder();
        builder.append("Content-Disposition: form-data; name=\"");
        builder.append(name);
        builder.append("\"");
        if (fileName != null) {
            builder.append("; filename=\"");
            builder.append(fileName);
            builder.append("\"");
        }
        builder.append(HttpHeaders.LINE_SEPARATOR);
        if (contentType != null) {
            builder.append("Content-Type: " + contentType);
            builder.append(HttpHeaders.LINE_SEPARATOR);
        }
        builder.append(HttpHeaders.LINE_SEPARATOR);
        return builder.toString().getBytes();
    }

    private static class ResultBuilder {

        private byte[] result;
        private int pos;

        private ResultBuilder(int size) {
            result = new byte[size];
        }

        private void add(byte[] data) {
            System.arraycopy(data, 0, result, pos, data.length);
            pos += data.length;
        }
    }
}
