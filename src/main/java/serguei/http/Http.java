package serguei.http;

import java.util.HashMap;
import java.util.Map;

/**
 * Class which allows to set certain global configuration parameters
 * 
 * @author Serguei Poliakov
 */
public class Http {

    private static final int DEFAULT_MAX_HEADER_LEN = 10240;

    private static Map<String, InputStreamWrapperFactory> contentEncodingStreams = new HashMap<>();
    private static int maxHeaderLen = DEFAULT_MAX_HEADER_LEN;

    private Http() {

    }

    /**
     * This allows to extend the library for content-encoding that are not supported yet.
     * 
     * For example Brotli (code "br") - it is not part of Java at the moment but one might use third-party library.
     * 
     * @param contentEncoding
     *            - the code as found in Content-Encoding header
     * @param factory
     *            - an instance of a class that will return a new stream wrapping the original stream
     */
    public static synchronized void setContentEncodingWrapper(String contentEncoding, InputStreamWrapperFactory factory) {
        Map<String, InputStreamWrapperFactory> newMap = new HashMap<>(contentEncodingStreams);
        newMap.put(contentEncoding, factory);
        contentEncodingStreams = newMap;
    }

    /**
     * Set maximum length of allowed header in bytes.
     * 
     * @param len
     *            - new max header length. Default - 10240
     */
    public static void setMaxHeaderLen(int len) {
        maxHeaderLen = len;
    }

    static void reset() {
        maxHeaderLen = DEFAULT_MAX_HEADER_LEN;
        contentEncodingStreams = new HashMap<>();
    }

    static InputStreamWrapperFactory getWrapperFactoryForContentEncoding(String contentEncoding) {
        return contentEncodingStreams.get(contentEncoding);
    }

    static int getMaxHeaderLen() {
        return maxHeaderLen;
    }
}
