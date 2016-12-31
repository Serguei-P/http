package serguei.http.examples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import serguei.http.BodyPart;
import serguei.http.HttpRequest;
import serguei.http.HttpResponseHeaders;
import serguei.http.HttpServer;
import serguei.http.HttpServerRequestHandler;

/**
 * This is an example of a very simple web server that shows a screen allowing to create a multi-part POST request
 * 
 * After running point your browser to http://localhost:8080/
 * 
 * @author Serguei
 *
 */
public class ServerWithPostForm implements Runnable {

    private static final int PORT = 8080;

    private final HttpServer server = new HttpServer(new RequestHandler(), PORT);
    private final BufferedReader reader;

    private String text = "";
    private byte[] image;

    public ServerWithPostForm(InputStream inputStream) {
        reader = new BufferedReader(new InputStreamReader(inputStream));
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

    public static void main(String[] args) {
        ServerWithPostForm process = new ServerWithPostForm(System.in);
        process.run();
        System.out.println("Exit");
    }

    private String buildPage() {
        StringBuilder builder = new StringBuilder();
        builder.append("<HTML><HEAD><TITLE>Form Test</TITLE></HEAD><BODY>");
        builder.append("<FORM action=\"/upload\" method=\"POST\" enctype=\"multipart/form-data\">");
        builder.append(text).append("<p>");
        if (image != null) {
            builder.append("<img src=\"/image.jpg\">");
        }
        builder.append("<TABLE>");
        builder.append("<TR><TH>Text</TH>").append("<TD><INPUT NAME=\"text\" value=\"").append(text).append("\"></TD></TR>");
        builder.append("<TR><TH>Image</TH><TD><INPUT TYPE=\"File\" NAME=\"image\"></TD></TR>");
        builder.append("<TR><TD COLSPAN=2><INPUT TYPE=\"Submit\" VALUE=\"Submit\"></TD></TR>");
        builder.append("</TABLE>");
        builder.append("</FORM></BODY></HTML>");
        return builder.toString();
    }

    private class RequestHandler implements HttpServerRequestHandler {

        @Override
        public void process(HttpRequest request, OutputStream outputStream) throws IOException {
            System.out.println(request.getMethod() + " " + request.getUrl());
            if (request.getUrl().getPath().equals("/favicon.ico")) {
                respondNotFound(outputStream);
            } else if (request.getUrl().getPath().equals("/upload") && request.hasMultipartBody()) {
                extractValues(request);
                respondWithForm(outputStream);
            } else if (request.getUrl().getPath().equals("/image.jpg")) {
                respondWithImage(outputStream);
            } else {
                respondWithForm(outputStream);
            }
            outputStream.flush();
        }

        private void respondNotFound(OutputStream outputStream) throws IOException {
            HttpResponseHeaders.notFound().write(outputStream);
        }

        private void extractValues(HttpRequest request) throws IOException {
            BodyPart bodyPart;
            while ((bodyPart = request.readNextBodyPart()) != null) {
                if (bodyPart.getName().equals("text")) {
                    text = bodyPart.getContentAsString();
                } else if (bodyPart.getName().equals("image")) {
                    image = bodyPart.getContentAsBytes();
                }
            }
        }

        private void respondWithImage(OutputStream outputStream) throws IOException {
            HttpResponseHeaders headers = HttpResponseHeaders.ok();
            headers.setHeader("Content-Length", Integer.toString(image.length));
            headers.write(outputStream);
            outputStream.write(image);
        }

        private void respondWithForm(OutputStream outputStream) throws IOException {
            String page = buildPage();
            byte[] pageBytes = page.getBytes("UTF-8");
            HttpResponseHeaders headers = HttpResponseHeaders.ok();
            headers.setHeader("Content-Length", Integer.toString(pageBytes.length));
            headers.write(outputStream);
            outputStream.write(pageBytes);
        }

    }

}
