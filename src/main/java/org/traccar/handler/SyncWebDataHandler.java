package org.traccar.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import static java.net.HttpURLConnection.HTTP_OK;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Formatter;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.BaseDataHandler;
import org.traccar.Context;
import org.traccar.config.Config;
import org.traccar.database.IdentityManager;
import org.traccar.helper.Checksum;
import org.traccar.helper.HttpUtil;
import org.traccar.model.Device;
import org.traccar.model.Group;
import org.traccar.model.Position;

/**
 *
 * @author Néstor Hernández Loli
 */
public class SyncWebDataHandler extends BaseDataHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncWebDataHandler.class);

    private final IdentityManager identityManager;
    private final ObjectMapper objectMapper;

    private final String url;
    private final String header;

    public SyncWebDataHandler() {
        this.identityManager = Context.getIdentityManager();
        this.objectMapper = Context.getObjectMapper();
        Config config = Context.getConfig();
        this.url = StringUtils.trimToEmpty(config.getString("nhl.baseUrl")) + config.getString("nhl.forward.url");
        this.header = config.getString("nhl.header");
    }

    private static String formatSentence(Position position) {

        StringBuilder s = new StringBuilder("$GPRMC,");

        try (Formatter f = new Formatter(s, Locale.ENGLISH)) {

            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
            calendar.setTimeInMillis(position.getFixTime().getTime());

            f.format("%1$tH%1$tM%1$tS.%1$tL,A,", calendar);

            double lat = position.getLatitude();
            double lon = position.getLongitude();

            f.format("%02d%07.4f,%c,", (int) Math.abs(lat), Math.abs(lat) % 1 * 60, lat < 0 ? 'S' : 'N');
            f.format("%03d%07.4f,%c,", (int) Math.abs(lon), Math.abs(lon) % 1 * 60, lon < 0 ? 'W' : 'E');

            f.format("%.2f,%.2f,", position.getSpeed(), position.getCourse());
            f.format("%1$td%1$tm%1$ty,,", calendar);
        }

        s.append(Checksum.nmea(s.toString()));

        return s.toString();
    }

    private String calculateStatus(Position position) {
        if (position.getAttributes().containsKey(Position.KEY_ALARM)) {
            return "0xF841"; // STATUS_PANIC_ON
        } else if (position.getSpeed() < 1.0) {
            return "0xF020"; // STATUS_LOCATION
        } else {
            return "0xF11C"; // STATUS_MOTION_MOVING
        }
    }

    public String formatRequest(Position position) throws UnsupportedEncodingException, JsonProcessingException {

        Device device = identityManager.getById(position.getDeviceId());

        String request = url
                .replace("{name}", URLEncoder.encode(device.getName(), StandardCharsets.UTF_8.name()))
                .replace("{uniqueId}", device.getUniqueId())
                .replace("{status}", device.getStatus())
                .replace("{deviceId}", String.valueOf(position.getDeviceId()))
                .replace("{protocol}", String.valueOf(position.getProtocol()))
                .replace("{deviceTime}", String.valueOf(position.getDeviceTime().getTime()))
                .replace("{fixTime}", String.valueOf(position.getFixTime().getTime()))
                .replace("{valid}", String.valueOf(position.getValid()))
                .replace("{latitude}", String.valueOf(position.getLatitude()))
                .replace("{longitude}", String.valueOf(position.getLongitude()))
                .replace("{altitude}", String.valueOf(position.getAltitude()))
                .replace("{speed}", String.valueOf(position.getSpeed()))
                .replace("{course}", String.valueOf(position.getCourse()))
                .replace("{accuracy}", String.valueOf(position.getAccuracy()))
                .replace("{statusCode}", calculateStatus(position));

        if (position.getAddress() != null) {
            request = request.replace(
                    "{address}", URLEncoder.encode(position.getAddress(), StandardCharsets.UTF_8.name()));
        }

        if (request.contains("{attributes}")) {
            String attributes = objectMapper.writeValueAsString(position.getAttributes());
            request = request.replace(
                    "{attributes}", URLEncoder.encode(attributes, StandardCharsets.UTF_8.name()));
        }

        if (request.contains("{gprmc}")) {
            request = request.replace("{gprmc}", formatSentence(position));
        }

        if (request.contains("{group}")) {
            String deviceGroupName = "";
            if (device.getGroupId() != 0) {
                Group group = Context.getGroupsManager().getById(device.getGroupId());
                if (group != null) {
                    deviceGroupName = group.getName();
                }
            }

            request = request.replace("{group}", URLEncoder.encode(deviceGroupName, StandardCharsets.UTF_8.name()));
        }

        return request;
    }

    @Override
    protected Position handlePosition(Position position) {
        String url;
        try {
            url = formatRequest(position);
        } catch (UnsupportedEncodingException | JsonProcessingException e) {
            throw new RuntimeException("Forwarding formatting error", e);
        }
        LOGGER.info("URL={},header={}, position={}", url, header, position);

        String queryString = url.substring(url.indexOf("?") + 1);
        String baseUrl = url.substring(0, url.indexOf("?"));
        long start = System.currentTimeMillis();
        try {
            executePost(baseUrl, queryString);
        } catch (IOException e) {
            LOGGER.error("Error sending data url={},queryString={}", baseUrl, queryString, e);
        } finally {
            long time = System.currentTimeMillis() - start;
            LOGGER.info("Position sent in {} ms", time);
        }
        return position;
    }

    private void executePost(String baseUrl, String queryString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl).openConnection();
        conn.setReadTimeout(30 * 1000);
        conn.setConnectTimeout(30 * 1000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        HttpUtil.setHeaders(header, conn);
        try (OutputStream out = new BufferedOutputStream(conn.getOutputStream())) {
            out.write(queryString.getBytes(StandardCharsets.UTF_8));
        }
        int respCode = conn.getResponseCode();
        String respBody = HttpUtil.readResponse(respCode, conn);
        if (respCode != HTTP_OK) {
            LOGGER.error("Error sending data url={},queryString={},responseCode={},responseBody={}",
                    baseUrl, queryString, respCode, respBody);
        }
    }

}
