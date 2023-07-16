package serguei.http;

import static org.junit.Assert.*;

import org.junit.Test;

import serguei.http.utils.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MultipartFormDataRequestBodyTest {

    private static final String text = "This is the text";
    private static final String fileContent = "This is file content";
    private static final String border = "BorderBorder";

    // @formatter:off
    private static final String[] REQUEST_DATA = {
        "--" + border,
        "Content-Disposition: form-data; name=\"text\"",
        "",
        text,
        "--" + border,
        "Content-Disposition: form-data; name=\"file1\"; filename=\"t1.txt\"",
        "Content-Type: text/plain",
        "",
        fileContent,
        "--" + border + "--",
        ""};
 // @formatter:on  

    @Test
    public void shouldGenerateBody() throws Exception {
        MultipartFormDataRequestBody body = new MultipartFormDataRequestBody(border);
        body.add(text, "text");
        body.add(fileContent, "file1", "t1.txt", "text/plain");

        byte[] result = body.getBody();

        assertEquals(Utils.concatWithDelimiter(REQUEST_DATA, "\r\n"), new String(result, "UTF-8"));
    }

    @Test
    public void shouldGenerateBodyFromInputStream() throws Exception {
        MultipartFormDataRequestBody body = new MultipartFormDataRequestBody(border);
        body.add(text, "text");
        body.add(() -> new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)), "file1", "t1.txt", "text/plain");

        byte[] result = body.getBody();

        assertEquals(Utils.concatWithDelimiter(REQUEST_DATA, "\r\n"), new String(result, "UTF-8"));
    }

    @Test
    public void shouldGenerateBodyAsStream() throws Exception {
        MultipartFormDataRequestBody body = new MultipartFormDataRequestBody(border);
        body.add(text, "text");
        body.add(() -> new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)), "file1", "t1.txt", "text/plain");

        InputStream inputStream = body.getBodyAsStream();
        String result = Utils.toString(inputStream, "UTF-8");

        assertEquals(Utils.concatWithDelimiter(REQUEST_DATA, "\r\n"), result);
    }

    @Test
    public void shouldGenerateBodyAsStreamAndReadItByByte() throws Exception {
        MultipartFormDataRequestBody body = new MultipartFormDataRequestBody(border);
        body.add(text, "text");
        body.add(() -> new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)), "file1", "t1.txt", "text/plain");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream inputStream = body.getBodyAsStream()) {
            int read;
            while ((read = inputStream.read()) != -1) {
                output.write(read);
            }
        }
        String result = output.toString(StandardCharsets.UTF_8.name());

        assertEquals(Utils.concatWithDelimiter(REQUEST_DATA, "\r\n"), result);
    }

    @Test
    public void shouldGenerateBodyAsStreamAndReadItBySmallBuffer() throws Exception {
        MultipartFormDataRequestBody body = new MultipartFormDataRequestBody(border);
        body.add(text, "text");
        body.add(() -> new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)), "file1", "t1.txt", "text/plain");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream inputStream = body.getBodyAsStream()) {
            byte[] buffer = new byte[4];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        String result = output.toString(StandardCharsets.UTF_8.name());

        assertEquals(Utils.concatWithDelimiter(REQUEST_DATA, "\r\n"), result);
    }
}
