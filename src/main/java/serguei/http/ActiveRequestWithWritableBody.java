package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ActiveRequestWithWritableBody {

    private final OutputStream bodyStream;
    private final InputStream inputStream;

    ActiveRequestWithWritableBody(OutputStream bodyStream, InputStream inputStream) {
        this.bodyStream = bodyStream;
        this.inputStream = inputStream;
    }

    public void write(byte[] buffer, int offset, int len) throws IOException {
        bodyStream.write(buffer, offset, len);
    }

    public HttpResponse readResponse() throws IOException {
        bodyStream.close();
        return new HttpResponse(inputStream);
    }
}
