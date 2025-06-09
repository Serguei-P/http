package serguei.http;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class NonCloseableOutputStream extends FilterOutputStream {

    /**
     * Wrapper to create a stream that would not be closed when close() method is called.
     *
     * @param out the underlying output stream to be assigned to
     *            the field <tt>this.out</tt> for later use, or
     *            <code>null</code> if this instance is to be
     *            created without an underlying stream.
     */
    public NonCloseableOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void close() throws IOException {
        flush();
    }
}
