package serguei.http;

import java.io.IOException;


public class HttpException extends IOException {

    private static final long serialVersionUID = -7774807879615495695L;

    public HttpException(String message) {
        super(message);
    }

}
