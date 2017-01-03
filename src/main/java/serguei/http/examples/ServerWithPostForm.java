package serguei.http.examples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import serguei.http.HttpRequest;
import serguei.http.HttpResponseHeaders;
import serguei.http.HttpServer;
import serguei.http.HttpServerRequestHandler;
import serguei.http.RequestValues;
import serguei.http.Utils;

/**
 * This is an example of a very simple web server that shows a screen allowing to create a normal and multi-part POST
 * requests
 * 
 * Just run it and, after running, point your browser to http://localhost:8080/
 * 
 * @author Serguei Poliakov
 *
 */
public class ServerWithPostForm implements Runnable {

    private static final int PORT = 8080;

    private final HttpServer server = new HttpServer(new RequestHandler(), PORT);
    private final BufferedReader reader;

    private String field1 = "";
    private String field2 = "";
    private String field3 = "";
    private String text = "";
    private byte[] image;

    public ServerWithPostForm(InputStream inputStream) {
        reader = new BufferedReader(new InputStreamReader(inputStream));
    }

    public static void main(String[] args) {
        ServerWithPostForm process = new ServerWithPostForm(System.in);
        process.run();
        System.out.println("Exit");
    }

    public int getPort() {
        return PORT;
    }

    @Override
    public void run() {
        try {
            server.start();
            while (true) {
                System.out.println("Server started");
                String line = reader.readLine();
                if (line != null && line.equalsIgnoreCase("quit")) {
                    server.stop();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isRunning() {
        return server.isRunning();
    }

    private class RequestHandler implements HttpServerRequestHandler {

        @Override
        public void process(HttpRequest request, OutputStream outputStream) throws IOException {
            System.out.println(request.getMethod() + " " + request.getUrl());
            if (request.getUrl().getPath().equals("/favicon.ico")) {
                respondNotFound(outputStream);
            } else if (request.getUrl().getPath().equals("/input")) {
                extractFormData(request);
                respondWithDataInputPage(outputStream);
            } else if (request.getUrl().getPath().equals("/upload")) {
                extractImageData(request);
                respondWithImageUploadPage(outputStream);
            } else if (request.getUrl().getPath().equals("/image.jpg")) {
                respondWithImage(outputStream);
            } else {
                respondWithMainPage(outputStream);
            }
            outputStream.flush();
        }

        private void extractFormData(HttpRequest request) throws IOException {
            RequestValues requestValues = request.readBodyAsValues();
            if (requestValues.getValue("field1") != null) {
                field1 = requestValues.getValue("field1");
            }
            if (requestValues.getValue("field2") != null) {
                field2 = requestValues.getValue("field2");
            }
            if (requestValues.getValue("field3") != null) {
                field3 = requestValues.getValue("field3");
            }
        }

        private void extractImageData(HttpRequest request) throws IOException {
            RequestValues requestValues = request.readBodyAsValues();
            if (requestValues.getValue("text") != null) {
                text = requestValues.getValue("text");
            }
            if (requestValues.getBytesValue("image") != null) {
                image = requestValues.getBytesValue("image");
            }
        }

        private void respondWithMainPage(OutputStream outputStream) throws IOException {
            String page = buildMainPage();
            byte[] pageBytes = page.getBytes("UTF-8");
            HttpResponseHeaders headers = HttpResponseHeaders.ok();
            headers.setHeader("Content-Length", Integer.toString(pageBytes.length));
            headers.write(outputStream);
            outputStream.write(pageBytes);
        }

        private void respondNotFound(OutputStream outputStream) throws IOException {
            HttpResponseHeaders.notFound().write(outputStream);
        }

        private void respondWithDataInputPage(OutputStream outputStream) throws IOException {
            String page = buildDataInputPage();
            byte[] pageBytes = page.getBytes("UTF-8");
            HttpResponseHeaders headers = HttpResponseHeaders.ok();
            headers.setHeader("Content-Length", Integer.toString(pageBytes.length));
            headers.write(outputStream);
            outputStream.write(pageBytes);
        }

        private void respondWithImageUploadPage(OutputStream outputStream) throws IOException {
            String page = buildImageUploadPage();
            byte[] pageBytes = page.getBytes("UTF-8");
            HttpResponseHeaders headers = HttpResponseHeaders.ok();
            headers.setHeader("Content-Length", Integer.toString(pageBytes.length));
            headers.write(outputStream);
            outputStream.write(pageBytes);
        }

        private void respondWithImage(OutputStream outputStream) throws IOException {
            HttpResponseHeaders headers = HttpResponseHeaders.ok();
            headers.setHeader("Content-Length", Integer.toString(image.length));
            headers.write(outputStream);
            outputStream.write(image);
        }

        private String buildMainPage() {
            StringBuilder builder = new StringBuilder();
            builder.append("<HTML><HEAD><TITLE>Form Test</TITLE></HEAD><BODY>");
            builder.append("<A HREF=\"/input\">Data form</A><P>");
            builder.append("<A HREF=\"/upload\">Upload Image form (multi-part request)</A>");
            builder.append("</BODY></HTML>");
            return builder.toString();
        }

        private String buildDataInputPage() {
            StringBuilder builder = new StringBuilder();
            builder.append("<HTML><HEAD><TITLE>Form Test</TITLE></HEAD><BODY>");
            builder.append("<FORM action=\"/input\" method=\"POST\">");
            builder.append("Field1: <b>").append(Utils.encodeHtml(field1)).append("</b><p>");
            builder.append("Field2: <b>").append(Utils.encodeHtml(field2)).append("</b><p>");
            builder.append("Field3: <b>").append(Utils.encodeHtml(field3)).append("</b><p>");
            builder.append("<TABLE>");
            builder.append(inputField("field1", field1));
            builder.append(inputField("field2", field2));
            builder.append(inputField("field3", field3));
            builder.append(submitRow());
            builder.append("</TABLE>");
            builder.append("</FORM></BODY></HTML>");
            return builder.toString();
        }

        private String buildImageUploadPage() {
            StringBuilder builder = new StringBuilder();
            builder.append("<HTML><HEAD><TITLE>Form Test</TITLE></HEAD><BODY>");
            builder.append("<FORM action=\"/upload\" method=\"POST\" enctype=\"multipart/form-data\">");
            builder.append("Text: <b>").append(text).append("</b><p>");
            if (image != null) {
                builder.append("<img src=\"/image.jpg\">");
            }
            builder.append("<TABLE>");
            builder.append(inputField("text", Utils.encodeHtml(text)));
            builder.append("<TR><TH>Image</TH><TD><INPUT TYPE=\"File\" NAME=\"image\"></TD></TR>");
            builder.append(submitRow());
            builder.append("</TABLE>");
            builder.append("</FORM></BODY></HTML>");
            return builder.toString();
        }

        private String inputField(String fieldName, String value) {
            return "<TR><TH>" + value + "</TH><TD><INPUT NAME=\"" + fieldName + "\" value=\"" + Utils.encodeHtml(value)
                    + "\"></TD></TR>";
        }

        private String submitRow() {
            return "<TR><TD><INPUT TYPE=\"Submit\" VALUE=\"Submit\"></TD><TD ALIGN=\"RIGHT\"><A HREF=\"/\">Return</A></TD></TR>";
        }

    }

}
