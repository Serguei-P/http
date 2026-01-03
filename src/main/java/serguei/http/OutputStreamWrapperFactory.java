package serguei.http;

import java.io.IOException;
import java.io.OutputStream;

public interface OutputStreamWrapperFactory {

    OutputStream wrap(OutputStream outputStream) throws IOException;
}
