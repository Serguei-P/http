package serguei.http;

import java.io.IOException;
import java.io.OutputStream;

public interface OutputStreamWrapperFactory {

    public OutputStream wrap(OutputStream outputStream) throws IOException;
}
