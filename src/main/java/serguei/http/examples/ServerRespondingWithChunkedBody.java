package serguei.http.examples;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

import serguei.http.ChunkedOutputStream;
import serguei.http.HttpRequest;
import serguei.http.HttpResponseHeaders;
import serguei.http.HttpServer;
import serguei.http.HttpServerRequestHandler;

/**
 * This is an example of a very simple web server that returns response with a chunked body
 * 
 * Just run it and, after running, point your browser to http://localhost:8080/
 * 
 * @author Serguei Poliakov
 *
 */
public class ServerRespondingWithChunkedBody implements Runnable {

    private static final int PORT = 8080;

    private final HttpServer server = new HttpServer(new RequestHandler(), PORT);
    private final BufferedReader reader;

    public ServerRespondingWithChunkedBody(InputStream inputStream) {
        reader = new BufferedReader(new InputStreamReader(inputStream));
    }

    public static void main(String[] args) {
        ServerRespondingWithChunkedBody process = new ServerRespondingWithChunkedBody(System.in);
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
            System.out.println("Server started on " + PORT);
            while (true) {
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
            HttpResponseHeaders headers = HttpResponseHeaders.ok();
            headers.setHeader("Transfer-Encoding", "chunked");
            headers.setHeader("Content-Encoding", "gzip");
            headers.write(outputStream);
            OutputStream stream = new GZIPOutputStream(new ChunkedOutputStream(outputStream));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, "UTF-8"));
            writer.write("<HTML><BODY><TABLE>");
            writer.write("<tr><td colspan=2>Properties</td></tr>");
            Properties properties = System.getProperties();
            for (Object key : properties.keySet()) {
                String property = key.toString();
                String value = properties.getProperty(property);
                String line = "<tr><td>" + property + "</td><td>" + value + "</td></tr>";
                writer.write(line);
            }
            writer.write("<tr><td colspan=2>--- END ---</td></tr>");
            writer.write("</TABLE></BODY></HTML>");
            writer.close(); // this does not close the underlying outputStream as ChunkedOutputStream.close() does not
                            // close it
        }
    }
}
