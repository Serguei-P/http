package serguei.http;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamWrapperFactory {

    InputStream wrap(InputStream inputStream) throws IOException;
}
