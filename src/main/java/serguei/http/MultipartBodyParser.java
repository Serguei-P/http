package serguei.http;

import java.io.IOException;
import java.io.InputStream;

public class MultipartBodyParser {

    private final InputStream inputStream;
    private final String border;
    private UpToBorderStreamReader reader;

    private int partCount = 0;

    MultipartBodyParser(InputStream inputStream, String border) {
        this.inputStream = inputStream;
        this.border = border;
        this.reader = new UpToBorderStreamReader(inputStream, ("--" + border).getBytes());
    }

    public BodyPart readNextBodyPart() throws IOException {
        if (partCount == 0) {
            reader.read();
            reader = new UpToBorderStreamReader(inputStream, ("\r\n--" + border).getBytes());
        }
        if (readToCheckIfFinalBorder()) {
            return null;
        }
        partCount++;
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
