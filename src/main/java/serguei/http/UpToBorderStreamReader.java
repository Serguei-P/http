package serguei.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class UpToBorderStreamReader {

    private final InputStream inputStream;
    private final byte[] border;

    private int lastByte = -1;
    private int replayPos = 0;
    private int maxReplayPos = 0;

    UpToBorderStreamReader(InputStream inputStream, byte[] border) {
        this.inputStream = inputStream;
        this.border = border;
    }

    public byte[] read() throws IOException {
        int matchedBytes = 0;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int ch;
        while ((ch = nextByte()) != -1) {
            byte b = (byte)ch;
            if (b == border[matchedBytes]) {
                matchedBytes++;
                if (matchedBytes == border.length) {
                    System.out.println("matched");
                    return outputStream.toByteArray();
                }
            } else {
                if (matchedBytes > 0) {
                    outputStream.write(border[replayPos]);
                    replayPos++;
                    maxReplayPos = matchedBytes;
                    lastByte = b;
                    matchedBytes = 0;
                } else {
                    outputStream.write(b);
                }
            }
        }
        if (matchedBytes > 0) {
            outputStream.write(border, 0, matchedBytes);
        }
        return outputStream.toByteArray();
    }

    private int nextByte() throws IOException {
        if (lastByte != -1) {
            if (replayPos < maxReplayPos) {
                return border[replayPos++];
            } else {
                replayPos = 0;
                maxReplayPos = 0;
                int b = lastByte;
                lastByte = -1;
                return b;
            }
        } else {
            return inputStream.read();
        }
    }

}
