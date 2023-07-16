package serguei.http.utils;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamProvider {

    InputStream getStream() throws IOException;
}
