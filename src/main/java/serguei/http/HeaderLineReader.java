package serguei.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

class HeaderLineReader {

    // This class is a bit messy due to performance optimisation

    private static final int MAX_LINE_LENGTH = 4096;
    private static final int BUFFER_SIZE = 128;
    private static final String EMPTY_LINE = "";
    private static final String HEADER_CODEPAGE = "ISO_8859_1";

    private final InputStream in;
    private final byte[] buffer;
    private StringBuilder sb = null;
    private int buffPos = 0;
    private boolean eof = false;

    HeaderLineReader(InputStream in) {
        this.in = in;
        this.buffer = new byte[BUFFER_SIZE];
    }

    public String readLine() throws IOException {
        if (eof) {
            return null;
        }
        boolean crFound = false;
        boolean crlfFound = false;
        boolean extraByte = false;
        int lineLength = buffPos;
        if (buffPos > 0 && buffer[buffPos - 1] == '\r') {
            crFound = true;
            lineLength--;
        }
        int ch;
        while ((ch = in.read()) != -1) {
            buffer[buffPos++] = (byte)ch;
            if (crlfFound) {
                if (ch != ' ' && ch != '\t') {
                    extraByte = true;
                    break;
                } else {
                    crlfFound = false;
                    crFound = false;
                }
            }
            if (ch == '\r') {
                crFound = true;
            } else if (ch == '\n' && crFound) {
                crlfFound = true;
                if (lineLength == 0) { // it starts with CRLF
                    eof = true;
                    return null;
                }
            } else {
                lineLength++;
                if (crFound) {
                    lineLength++;
                    crFound = false;
                }
            }
            if (lineLength > MAX_LINE_LENGTH) {
                throw new HttpException("Line is too long while reading headers, limit " + MAX_LINE_LENGTH);
            }
            if (buffPos >= buffer.length) {
                int reduction = 0;
                if (crlfFound) {
                    reduction += 2;
                } else if (crFound) {
                    reduction++;
                }
                String chunk = new String(buffer, 0, buffPos - reduction, HEADER_CODEPAGE);
                if (sb == null) {
                    sb = new StringBuilder(chunk);
                } else {
                    sb.append(chunk);
                }
                for (int i = 0; i < reduction; i++) {
                    buffer[i] = buffer[buffPos - reduction + i];
                }
                buffPos = reduction;
            }
        }
        if (crlfFound) {
            buffPos -= 2;
            if (extraByte) {
                buffPos--;
            }
        }
        String result;
        if (sb == null || sb.length() == 0) {
            // short lines
            if (buffPos > 0) {
                result = rtrim(returnLastChunk(buffPos));
            } else {
                eof = true;
                result = null;
            }
        } else {
            // long lines
            sb.append(returnLastChunk(buffPos));
            result = rtrim(sb.toString());
            sb.setLength(0);
        }
        if (extraByte) {
            buffer[0] = buffer[buffPos + 2];
            buffPos = 1;
        } else {
            buffPos = 0;
        }
        return result;
    }

    private String returnLastChunk(int buffPos) throws UnsupportedEncodingException {
        return buffPos != 0 ? new String(buffer, 0, buffPos, HEADER_CODEPAGE) : EMPTY_LINE;
    }

    private static String rtrim(String line) {
        int len = line.length();
        while (len > 0 && line.charAt(len - 1) <= ' ') {
            len--;
        }
        return len < line.length() ? line.substring(0, len) : line;
    }

}
