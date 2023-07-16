package serguei.http;

import serguei.http.utils.InputStreamProvider;
import serguei.http.utils.MultipartInputStream;
import serguei.http.utils.Utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static serguei.http.HttpBody.BODY_CHARSET;

public class MultipartFormDataRequestBody {

    private static final byte[] END = ("--" + HttpHeaders.LINE_SEPARATOR).getBytes(BODY_CHARSET) ;

    private final byte[] border;

    private final List<InputStreamHolder> dataList = new ArrayList<>();

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
        add(data.getBytes(BODY_CHARSET), name, fileName, contentType);
    }

    public void add(byte[] data, String name, String fileName, String contentType) {
        add(new InputStreamHolder(data), name, fileName, contentType);
    }

    public void add(InputStreamProvider inputStreamProvider, String name, String fileName, String contentType) {
        add(new InputStreamHolder(inputStreamProvider), name, fileName, contentType);
    }

    private void add(InputStreamHolder inputStreamHolder, String name, String fileName, String contentType) {
        dataList.add(new InputStreamHolder(this.border));
        dataList.add(new InputStreamHolder(HttpHeaders.LINE_SEPARATOR_BYTES));
        dataList.add(new InputStreamHolder(buildHeaders(name, fileName, contentType)));
        dataList.add(inputStreamHolder);
        dataList.add(new InputStreamHolder(HttpHeaders.LINE_SEPARATOR_BYTES));
    }

    public byte[] getBody() throws IOException {
        int len = 0;
        for (InputStreamHolder data : dataList) {
            len += data.getData().length;
        }
        len += border.length;
        len += END.length;
        ResultBuilder result = new ResultBuilder(len);
        for (InputStreamHolder data : dataList) {
            result.add(data.getData());
        }
        result.add(border);
        result.add(END);
        return result.result;
    }

    public InputStream getBodyAsStream() throws IOException {
        List<InputStreamProvider> streams = new ArrayList<>(dataList.size() + 1);
        for (InputStreamHolder data : dataList) {
            streams.add(data.getInputStreamProvider());
        }
        streams.add(() -> new ByteArrayInputStream(border));
        streams.add(() -> new ByteArrayInputStream(END));
        return new MultipartInputStream(streams);
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
        return builder.toString().getBytes(BODY_CHARSET);
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

    private static class InputStreamHolder {

        private InputStreamProvider inputStreamProvider;
        private byte[] data;

        private InputStreamHolder(InputStreamProvider inputStreamProvider) {
            this.inputStreamProvider = inputStreamProvider;
        }

        private InputStreamHolder(byte[] data) {
            this.data = data;
        }

        public byte[] getData() throws IOException {
            if (data == null) {
                try (InputStream inputStream = inputStreamProvider.getStream()) {
                    data = Utils.toByteArray(inputStream);
                }
            }
            return data;
        }

        public InputStreamProvider getInputStreamProvider() {
            if (inputStreamProvider != null) {
                return inputStreamProvider;
            } else {
                return () -> new ByteArrayInputStream(data);
            }
        }
    }
}
