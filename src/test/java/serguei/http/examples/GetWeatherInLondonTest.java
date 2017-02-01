package serguei.http.examples;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

public class GetWeatherInLondonTest {

    private static final String RESULT_PREFIX = "Temperature in London is ";

    // @Test
    public void shoudReturnWeatherInLondong() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GetWeatherInLondon weather = new GetWeatherInLondon(output);

        weather.doTheJob();

        String result = output.toString();
        assertTrue("Wrong response: " + result, result.contains(RESULT_PREFIX));
        String value = result.substring(result.indexOf(RESULT_PREFIX) + RESULT_PREFIX.length());
        assertNotNull(Double.valueOf(value)); // this will fail if the value is not a number
    }
}
