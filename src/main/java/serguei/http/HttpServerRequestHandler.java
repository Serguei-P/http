package serguei.http;

import java.io.IOException;
import java.io.OutputStream;

public interface HttpServerRequestHandler {

    public void process(HttpRequest request, OutputStream outputStream) throws IOException;

}
