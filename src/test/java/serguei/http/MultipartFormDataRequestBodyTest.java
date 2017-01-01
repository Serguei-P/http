package serguei.http;

import static org.junit.Assert.*;

import org.junit.Test;

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
        "--" + border + "--"};
 // @formatter:on  

    @Test
    public void shouldGenerateBody() throws Exception {
        MultipartFormDataRequestBody body = new MultipartFormDataRequestBody(border);
        body.add(text, "text");
        body.add(fileContent, "file1", "t1.txt", "text/plain");

        byte[] result = body.getBody();

        System.out.println(new String(result, "UTF-8"));
        System.out.println("---");
        System.out.println(getRequestBody());
        assertEquals(getRequestBody(), new String(result, "UTF-8"));
    }

    private String getRequestBody() {
        StringBuilder builder = new StringBuilder();
        for (String line : REQUEST_DATA) {
            builder.append(line);
            builder.append("\r\n");
        }
        return builder.toString();
    }

}
