package serguei.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

class MarkAndResetInputStream extends FilterInputStream {

    private byte[] rememberedData;
    private int rememberedSize;
    private int rememberedPos;
    private boolean marked = false;

    MarkAndResetInputStream(InputStream input) {
        super(input);
    }

    @Override
    public synchronized void mark(int readLimit) {
        if (!marked) {
            marked = true;
            if (readLimit > 0) {
                rememberedData = new byte[readLimit];
            } else {
                rememberedData = new byte[512];
            }
            rememberedPos = 0;
        }
    }

    @Override
    public int read() throws IOException {
        if (marked) {
            int result = in.read();
            if (result >= 0) {
                resizeIfNecessary(1);
                rememberedData[rememberedSize++] = (byte)result;
            }
            return result;
        } else if (rememberedData != null) {
            return readWithReplay();
        } else {
            return in.read();
        }
    }

    @Override
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        if (marked) {
            int result = in.read(b, off, len);
            if (result > 0) {
                resizeIfNecessary(result);
                System.arraycopy(b, off, rememberedData, rememberedSize, result);
                rememberedSize += result;
            }
            return result;
        } else if (rememberedData != null) {
            return readWithReplay(b, off, len);
        } else {
            return in.read(b, off, len);
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if (marked) {
            resizeIfNecessary((int)n); // we not expecting to skip for that long after marking position
            int total = 0;
            int len;
            do {
                len = in.read(rememberedData, rememberedSize, (int)n);
                if (len > 0) {
                    rememberedSize += len;
                    total += len;
                }
            } while (total < n && len > 0);
            return total;
        } else if (rememberedData != null) {
            return skipWithReplay(n);
        } else {
            return in.skip(n);
        }
    }

    @Override
    public int available() throws IOException {
        return bytesToReplay() + in.available();
    }

    @Override
    public void reset() {
        marked = false;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    public InputStream getOriginalStream() {
        return in;
    }

    private void resizeIfNecessary(int extraLength) {
        if (rememberedData.length < rememberedSize + extraLength) {
            if (extraLength == 1) {
                extraLength = 16;
            }
            byte[] newBuffer = new byte[rememberedSize + extraLength];
            System.arraycopy(rememberedData, 0, newBuffer, 0, rememberedSize);
            rememberedData = newBuffer;
        }
    }

    private int readWithReplay(byte[] buffer, int off, int len) throws IOException {
        int result;
        if (bytesToReplay() >= len) {
            System.arraycopy(rememberedData, rememberedPos, buffer, off, len);
            rememberedPos += len;
            result = len;
        } else {
            int replayLen = bytesToReplay();
            System.arraycopy(rememberedData, rememberedPos, buffer, off, replayLen);
            rememberedPos += replayLen;
            if (in.available() > 0) {
                result = replayLen + in.read(buffer, off + replayLen, len - replayLen);
            } else {
                result = replayLen;
            }
        }
        if (bytesToReplay() <= 0) {
            rememberedData = null;
        }
        return result;
    }

    private int readWithReplay() {
        int result = rememberedData[rememberedPos++] & 0xff;
        if (bytesToReplay() <= 0) {
            rememberedData = null;
        }
        return result;
    }

    private long skipWithReplay(long n) throws IOException {
        long result;
        if (bytesToReplay() >= n) {
            rememberedPos += n;
            result = n;
        } else {
            int replayLen = bytesToReplay();
            rememberedPos += replayLen;
            result = replayLen + in.skip(n - replayLen);
        }
        if (bytesToReplay() <= 0) {
            rememberedData = null;
        }
        return result;
    }

    private int bytesToReplay() {
        if (rememberedData != null) {
            return rememberedSize - rememberedPos;
        } else {
            return 0;
        }
    }

}
