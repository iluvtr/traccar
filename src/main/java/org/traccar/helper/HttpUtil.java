package org.traccar.helper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import static java.net.HttpURLConnection.HTTP_OK;
import java.util.Scanner;

/**
 *
 * @author Néstor Hernández Loli
 */
public final class HttpUtil {

    private HttpUtil() {
    }

    public static void setHeaders(String header, HttpURLConnection conn) {
        if (header != null && !header.isEmpty()) {
            for (String line : header.split("\\r?\\n")) {
                String[] values = line.split(":", 2);
                conn.setRequestProperty(values[0].trim(), values[1].trim());
            }
        }
    }

    public static String readResponse(int respCode, HttpURLConnection conn) throws IOException {
        try (InputStream input = new BufferedInputStream(
                respCode == HTTP_OK ? conn.getInputStream() : conn.getErrorStream())) {
            Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
