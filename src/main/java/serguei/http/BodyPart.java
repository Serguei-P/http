package serguei.http;

import java.io.UnsupportedEncodingException;

public class BodyPart {

    private final HttpHeaders headers;
    private final byte[] body;

    public BodyPart(HttpHeaders headers, byte[] body) {
        this.headers = headers;
        this.body = body;
    }

    public String getName() {
        return headers.getHeaderValue("Content-Disposition", "name");
    }

    public String getFilename() {
        return headers.getHeaderValue("Content-Disposition", "filename");
    }

    public String getContentType() {
        return headers.getHeader("Content-Type");
    }

    public byte[] getContentAsBytes() {
        return body;
    }

    public String getContentAsString() {
        try {
            return new String(body, HttpBody.BODY_CODEPAGE);
        } catch (UnsupportedEncodingException e) {
            // should not happen
            throw new RuntimeException("Encoding error, " + HttpBody.BODY_CODEPAGE, e);
        }
    }

}
