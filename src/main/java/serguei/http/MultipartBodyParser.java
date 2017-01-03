package serguei.http;

import java.io.IOException;
import java.io.InputStream;

class MultipartBodyParser {

    private final InputStream inputStream;
    private final String border;
    private UpToBorderStreamReader reader;

    private boolean firstBorderRead;

    MultipartBodyParser(InputStream inputStream, String border) {
        this.inputStream = inputStream;
        this.border = border;
        this.reader = new UpToBorderStreamReader(inputStream, ("--" + border).getBytes());
    }

    public BodyPart readNextBodyPart() throws IOException {
        if (!firstBorderRead) {
            reader.read();
            reader = new UpToBorderStreamReader(inputStream, ("\r\n--" + border).getBytes());
            firstBorderRead = true;
        }
        if (readToCheckIfFinalBorder()) {
            inputStream.skip(2); // final CRLF
            return null;
        }
        HttpHeaders headers = new MultipartHttpHeaders();
        headers.readHeaders(new HeaderLineReader(inputStream));
        byte[] body = reader.read();
        return new BodyPart(headers, body);
    }

    private boolean readToCheckIfFinalBorder() throws IOException {
        int firstByte = inputStream.read();
        if (firstByte == -1) {
            return true;
        }
        int secondByte = inputStream.read();
        if (secondByte == -1) {
            return true;
        }
        return firstByte == '-' && secondByte == '-';
    }

    private class MultipartHttpHeaders extends HttpHeaders {
    }

}
