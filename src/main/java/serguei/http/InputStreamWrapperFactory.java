package serguei.http;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamWrapperFactory {

    public InputStream wrap(InputStream inputStream) throws IOException;
}
