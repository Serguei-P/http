package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class HeaderLineReader {

    private static final int MAX_LINE_LENGTH = 4096;
    private static final int BUFFER_SIZE = 128;
    private static final String EMPTY_LINE = "";

    private final InputStream in;
    private final byte[] buffer;
    private StringBuilder sb = null;

    public HeaderLineReader(InputStream in) {
        this.in = in;
        this.buffer = new byte[BUFFER_SIZE];
    }

    public String readLine() throws IOException {
        int lineLength = 0;
        int buffPos = 0;
        int ch;
        if (sb != null) {
            sb.setLength(0);
        }
        while ((ch = in.read()) != -1 && ch != '\n') {
            buffer[buffPos++] = (byte)ch;
            lineLength++;
            if (lineLength > MAX_LINE_LENGTH) {
                throw new HttpException("Line too long");
            }
            if (buffPos >= buffer.length) {
                String chunk = new String(buffer, 0, buffPos, "ISO_8859_1");
                if (sb == null) {
                    sb = new StringBuilder(chunk);
                } else {
                    sb.append(chunk);
                }
                buffPos = 0;
            }
        }
        if (sb == null || sb.length() == 0) {
            // short lines
            if (lineLength > 0 || ch != -1) {
                // empty non-EOF lines are returned so caller can detect
                // the beginning of message body and read some other way
                return rtrim(returnLastChunk(buffPos));
            } else {
                // LF EOF
                return null;
            }
        } else {
            // long lines
            sb.append(returnLastChunk(buffPos));
            return rtrim(sb.toString());
        }
    }

    private String returnLastChunk(int buffPos) throws UnsupportedEncodingException {
        if (buffPos > 0 && buffer[buffPos - 1] == '\r') {
            buffPos--;
        }
        return buffPos != 0 ? new String(buffer, 0, buffPos, "ISO_8859_1") : EMPTY_LINE;
    }

    private static String rtrim(String line) {
        int len = line.length();
        while (len > 0 && line.charAt(len - 1) <= ' ') {
            len--;
        }
        return len < line.length() ? line.substring(0, len) : line;
    }
    
}
