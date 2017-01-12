package serguei.http.examples;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.security.cert.X509Certificate;

import serguei.http.HttpClientConnection;
import serguei.http.HttpRequestHeaders;
import serguei.http.HttpResponse;

/**
 * This is an example of using HttpClientConnection
 * 
 * This program makes request to a free weather API with a request for weather in London and then extract temperature
 * from returned JSON. The temperature is output to console.
 * 
 * If we use HTTPS version of the API (defined by WEATHER_API_URL value), we also output some extra SSL parameters
 * 
 * I can't guarantee that this site will always work as I have no relation to it.
 * 
 * @author Serguei Poliakov
 *
 */
public class GetWeatherInLondon {

    // replace https with http if accessing non-secure HTTP:
    private static final String WEATHER_API_URL = "https://weathers.co/api.php";
    private static final String CITY = "London";
    private static final int OK_STATUS = 200;

    private final BufferedWriter output;

    public GetWeatherInLondon(OutputStream output) {
        this.output = new BufferedWriter(new OutputStreamWriter(output));
    }

    public static void main(String[] args) {
        GetWeatherInLondon process = new GetWeatherInLondon(System.out);
        try {
            process.doTheJob();
            System.out.println("Finished");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void doTheJob() throws IOException {
        URL url = new URL(WEATHER_API_URL);
        boolean ssl = url.getProtocol().equals("https");
        int port = ssl ? 443 : 80;
        try (HttpClientConnection connection = new HttpClientConnection(url.getHost(), port)) {
            if (ssl) {
                // the following will not validate if the certificate is valid, for validation use
                // startHandshakeAndValidate
                connection.startHandshake();
                outputSslParameters(connection);
            }
            HttpRequestHeaders requestHeaders = HttpRequestHeaders.getRequest(WEATHER_API_URL + "?city=" + CITY);
            HttpResponse response = connection.send(requestHeaders);
            if (response.getStatusCode() == OK_STATUS) {
                String responseBody = response.readBodyAsString();
                String temperature = getTemperatureFromResponseBody(responseBody);
                output.write("Temperature in " + CITY + " is " + temperature);
                output.newLine();
            } else {
                output.write("Incorrect response status: " + response.getStatusCode() + " " + response.getReason());
                output.newLine();
            }
            output.flush();
        }
    }

    private String getTemperatureFromResponseBody(String json) throws IOException {
        // don't want to import any JSON parsing library - quick and dirty solution
        String valueName = "\"temperature\"";
        int pos = json.indexOf(valueName);
        if (pos > 0) {
            int start = json.indexOf('"', pos + valueName.length());
            if (start > 0) {
                start++;
                int end = json.indexOf('"', start);
                if (end > 0) {
                    return json.substring(start, end);
                }
            }
        }
        throw new IOException("Wrong JSON received: " + json);
    }

    private void outputSslParameters(HttpClientConnection connection) throws IOException {
        output.write("TLS version used: " + connection.getNegotiatedTlsProtocol().toJdkString());
        output.newLine();
        output.write("Negotiated cipher suite: " + connection.getNegotiatedCipher());
        output.newLine();
        output.write("Received certificates:");
        output.newLine();
        for (X509Certificate certificate : connection.getTlsCertificates()) {
            output.write("  " + certificate.getSubjectDN().getName());
            output.newLine();
        }
        output.write("-----------------------");
        output.newLine();
    }

}
