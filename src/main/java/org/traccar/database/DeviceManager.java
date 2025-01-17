/*
 * Copyright 2016 - 2018 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.database;

import java.io.IOException;
import java.net.HttpURLConnection;
import static java.net.HttpURLConnection.HTTP_OK;
import java.net.URL;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.Context;
import org.traccar.helper.HttpUtil;
import org.traccar.model.Device;
import org.traccar.model.DeviceState;
import org.traccar.model.DeviceAccumulators;
import org.traccar.model.Group;
import org.traccar.model.Position;
import org.traccar.model.Server;

public class DeviceManager extends BaseObjectManager<Device> implements IdentityManager, ManagableObjects {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceManager.class);

    public static final long DEFAULT_REFRESH_DELAY = 300;

    private final Config config;
    private final long dataRefreshDelay;
    private boolean lookupGroupsAttribute;

    private Map<String, Device> devicesByUniqueId;
    private Map<String, Device> devicesByPhone;
    private AtomicLong devicesLastUpdate = new AtomicLong();

    private final Map<Long, Position> positions = new ConcurrentHashMap<>();

    private final Map<Long, DeviceState> deviceStates = new ConcurrentHashMap<>();

    public DeviceManager(DataManager dataManager) {
        super(dataManager, Device.class);
        this.config = Context.getConfig();
        if (devicesByPhone == null) {
            devicesByPhone = new ConcurrentHashMap<>();
        }
        if (devicesByUniqueId == null) {
            devicesByUniqueId = new ConcurrentHashMap<>();
        }
        dataRefreshDelay = config.getLong("database.refreshDelay", DEFAULT_REFRESH_DELAY) * 1000;
        lookupGroupsAttribute = config.getBoolean("deviceManager.lookupGroupsAttribute");
        refreshLastPositions();
    }

    @Override
    public long addUnknownDevice(String uniqueId) {
        Device device = new Device();
        device.setName(uniqueId);
        device.setUniqueId(uniqueId);
        device.setCategory(Context.getConfig().getString("database.registerUnknown.defaultCategory"));

        long defaultGroupId = Context.getConfig().getLong("database.registerUnknown.defaultGroupId");
        if (defaultGroupId != 0) {
            device.setGroupId(defaultGroupId);
        }

        try {
            addItem(device);

            LOGGER.info("Automatically registered device " + uniqueId);

            if (defaultGroupId != 0) {
                Context.getPermissionsManager().refreshDeviceAndGroupPermissions();
                Context.getPermissionsManager().refreshAllExtendedPermissions();
            }

            return device.getId();
        } catch (SQLException e) {
            LOGGER.warn("Automatic device registration error", e);
            return 0;
        }
    }

    public void updateDeviceCache(boolean force) throws SQLException {
        long lastUpdate = devicesLastUpdate.get();
        if ((force || System.currentTimeMillis() - lastUpdate > dataRefreshDelay)
                && devicesLastUpdate.compareAndSet(lastUpdate, System.currentTimeMillis())) {
            refreshItems();
        }
    }

    @Override
    public Device getByUniqueId(String uniqueId) throws SQLException {
        boolean forceUpdate = !devicesByUniqueId.containsKey(uniqueId) && !config.getBoolean("database.ignoreUnknown");

        updateDeviceCache(forceUpdate);

        return devicesByUniqueId.get(uniqueId);
    }

    public Device getDeviceByPhone(String phone) {
        return devicesByPhone.get(phone);
    }

    @Override
    public Set<Long> getAllItems() {
        Set<Long> result = super.getAllItems();
        if (result.isEmpty()) {
            try {
                updateDeviceCache(true);
            } catch (SQLException e) {
                LOGGER.warn("Update device cache error", e);
            }
            result = super.getAllItems();
        }
        return result;
    }

    public Collection<Device> getAllDevices() {
        return getItems(getAllItems());
    }

    public Set<Long> getAllUserItems(long userId) {
        return Context.getPermissionsManager().getDevicePermissions(userId);
    }

    @Override
    public Set<Long> getUserItems(long userId) {
        if (Context.getPermissionsManager() != null) {
            Set<Long> result = new HashSet<>();
            for (long deviceId : Context.getPermissionsManager().getDevicePermissions(userId)) {
                Device device = getById(deviceId);
                if (device != null && !device.getDisabled()) {
                    result.add(deviceId);
                }
            }
            return result;
        } else {
            return new HashSet<>();
        }
    }

    public Set<Long> getAllManagedItems(long userId) {
        Set<Long> result = new HashSet<>();
        result.addAll(getAllUserItems(userId));
        for (long managedUserId : Context.getUsersManager().getUserItems(userId)) {
            result.addAll(getAllUserItems(managedUserId));
        }
        return result;
    }

    @Override
    public Set<Long> getManagedItems(long userId) {
        Set<Long> result = new HashSet<>();
        result.addAll(getUserItems(userId));
        for (long managedUserId : Context.getUsersManager().getUserItems(userId)) {
            result.addAll(getUserItems(managedUserId));
        }
        return result;
    }

    private void putUniqueDeviceId(Device device) {
        if (devicesByUniqueId == null) {
            devicesByUniqueId = new ConcurrentHashMap<>(getAllItems().size());
        }
        devicesByUniqueId.put(device.getUniqueId(), device);
    }

    private void putPhone(Device device) {
        if (devicesByPhone == null) {
            devicesByPhone = new ConcurrentHashMap<>(getAllItems().size());
        }
        devicesByPhone.put(device.getPhone(), device);
    }

    @Override
    protected void addNewItem(Device device) {
        super.addNewItem(device);
        putUniqueDeviceId(device);
        if (device.getPhone() != null  && !device.getPhone().isEmpty()) {
            putPhone(device);
        }
        if (Context.getGeofenceManager() != null) {
            Position lastPosition = getLastPosition(device.getId());
            if (lastPosition != null) {
                device.setGeofenceIds(Context.getGeofenceManager().getCurrentDeviceGeofences(lastPosition));
            }
        }
    }

    @Override
    protected void updateCachedItem(Device device) {
        Device cachedDevice = getById(device.getId());
        cachedDevice.setName(device.getName());
        cachedDevice.setGroupId(device.getGroupId());
        cachedDevice.setCategory(device.getCategory());
        cachedDevice.setContact(device.getContact());
        cachedDevice.setModel(device.getModel());
        cachedDevice.setDisabled(device.getDisabled());
        cachedDevice.setAttributes(device.getAttributes());
        if (!device.getUniqueId().equals(cachedDevice.getUniqueId())) {
            devicesByUniqueId.remove(cachedDevice.getUniqueId());
            cachedDevice.setUniqueId(device.getUniqueId());
            putUniqueDeviceId(cachedDevice);
        }
        if (device.getPhone() != null && !device.getPhone().isEmpty()
                && !device.getPhone().equals(cachedDevice.getPhone())) {
            String phone = cachedDevice.getPhone();
            if (phone != null && !phone.isEmpty()) {
                devicesByPhone.remove(phone);
            }
            cachedDevice.setPhone(device.getPhone());
            putPhone(cachedDevice);
        }
    }

    @Override
    protected void removeCachedItem(long deviceId) {
        Device cachedDevice = getById(deviceId);
        if (cachedDevice != null) {
            String deviceUniqueId = cachedDevice.getUniqueId();
            String phone = cachedDevice.getPhone();
            super.removeCachedItem(deviceId);
            devicesByUniqueId.remove(deviceUniqueId);
            if (phone != null && !phone.isEmpty()) {
                devicesByPhone.remove(phone);
            }
        }
        positions.remove(deviceId);
    }

    public void updateDeviceStatus(Device device) throws SQLException {
        getDataManager().updateDeviceStatus(device);
        Device cachedDevice = getById(device.getId());
        if (cachedDevice != null) {
            cachedDevice.setStatus(device.getStatus());
        }
    }

    private void refreshLastPositions() {
        if (getDataManager() != null) {
            try {
                for (Position position : getDataManager().getLatestPositions()) {
                    positions.put(position.getDeviceId(), position);
                }
            } catch (SQLException error) {
                LOGGER.warn("Load latest positions error", error);
            }
        }
    }

    public boolean isLatestPosition(Position position) {
        Position lastPosition = getLastPosition(position.getDeviceId());
        return lastPosition == null || position.getFixTime().compareTo(lastPosition.getFixTime()) >= 0;
    }

    public void updateLatestPosition(Position position) throws SQLException {

        if (isLatestPosition(position)) {

            getDataManager().updateLatestPosition(position);

            Device device = getById(position.getDeviceId());
            if (device != null) {
                device.setPositionId(position.getId());
            }

            positions.put(position.getDeviceId(), position);

            if (Context.getConnectionManager() != null) {
                Context.getConnectionManager().updatePosition(position);
            }
        }
    }

    @Override
    public Position getLastPosition(long deviceId) {
        return positions.get(deviceId);
    }

    public Collection<Position> getInitialState(long userId) {

        List<Position> result = new LinkedList<>();

        if (Context.getPermissionsManager() != null) {
            for (long deviceId : Context.getPermissionsManager().getUserAdmin(userId)
                    ? getAllUserItems(userId) : getUserItems(userId)) {
                if (positions.containsKey(deviceId)) {
                    result.add(positions.get(deviceId));
                }
            }
        }

        return result;
    }

    @Override
    public boolean lookupAttributeBoolean(
            long deviceId, String attributeName, boolean defaultValue, boolean lookupConfig) {
        Object result = lookupAttribute(deviceId, attributeName, lookupConfig);
        if (result != null) {
            return result instanceof String ? Boolean.parseBoolean((String) result) : (Boolean) result;
        }
        return defaultValue;
    }

    @Override
    public String lookupAttributeString(
            long deviceId, String attributeName, String defaultValue, boolean lookupConfig) {
        Object result = lookupAttribute(deviceId, attributeName, lookupConfig);
        return result != null ? (String) result : defaultValue;
    }

    @Override
    public int lookupAttributeInteger(long deviceId, String attributeName, int defaultValue, boolean lookupConfig) {
        Object result = lookupAttribute(deviceId, attributeName, lookupConfig);
        if (result != null) {
            return result instanceof String ? Integer.parseInt((String) result) : ((Number) result).intValue();
        }
        return defaultValue;
    }

    @Override
    public long lookupAttributeLong(
            long deviceId, String attributeName, long defaultValue, boolean lookupConfig) {
        Object result = lookupAttribute(deviceId, attributeName, lookupConfig);
        if (result != null) {
            return result instanceof String ? Long.parseLong((String) result) : ((Number) result).longValue();
        }
        return defaultValue;
    }

    public double lookupAttributeDouble(
            long deviceId, String attributeName, double defaultValue, boolean lookupConfig) {
        Object result = lookupAttribute(deviceId, attributeName, lookupConfig);
        if (result != null) {
            return result instanceof String ? Double.parseDouble((String) result) : ((Number) result).doubleValue();
        }
        return defaultValue;
    }

    private Object lookupAttribute(long deviceId, String attributeName, boolean lookupConfig) {
        Object result = null;
        Device device = getById(deviceId);
        if (device != null) {
            result = device.getAttributes().get(attributeName);
            if (result == null && lookupGroupsAttribute) {
                long groupId = device.getGroupId();
                while (groupId != 0) {
                    Group group = Context.getGroupsManager().getById(groupId);
                    if (group != null) {
                        result = group.getAttributes().get(attributeName);
                        if (result != null) {
                            break;
                        }
                        groupId = group.getGroupId();
                    } else {
                        groupId = 0;
                    }
                }
            }
            if (result == null) {
                if (lookupConfig) {
                    result = Context.getConfig().getString(attributeName);
                } else {
                    Server server = Context.getPermissionsManager().getServer();
                    result = server.getAttributes().get(attributeName);
                }
            }
        }
        return result;
    }

    public void resetDeviceAccumulators(DeviceAccumulators deviceAccumulators) throws SQLException {
        Position last = positions.get(deviceAccumulators.getDeviceId());
        if (last != null) {
            if (deviceAccumulators.getTotalDistance() != null) {
                last.getAttributes().put(Position.KEY_TOTAL_DISTANCE, deviceAccumulators.getTotalDistance());
            }
            if (deviceAccumulators.getHours() != null) {
                last.getAttributes().put(Position.KEY_HOURS, deviceAccumulators.getHours());
            }
            getDataManager().addObject(last);
            updateLatestPosition(last);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public DeviceState getDeviceState(long deviceId) {
        DeviceState deviceState = deviceStates.get(deviceId);
        if (deviceState == null) {
            deviceState = new DeviceState();
            deviceStates.put(deviceId, deviceState);
        }
        return deviceState;
    }

    public void setDeviceState(long deviceId, DeviceState deviceState) {
        deviceStates.put(deviceId, deviceState);
    }

    @Override
    public boolean canCreateUnknownDevice(String uniqueId) {
        String baseUrl = StringUtils.trimToEmpty(config.getString("nhl.baseUrl"));
        String canCreateUrl = StringUtils.trimToEmpty(config.getString("nhl.canCreateUnknownDevice.url"));
        LOGGER.info("url={},canCreateUrl={}", baseUrl, canCreateUrl);
        if (canCreateUrl.isEmpty()) {
            return false;
        }
        String url = baseUrl + canCreateUrl;
        String header = config.getString("nhl.header");
        url = url.replace("{uniqueId}", uniqueId);
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setReadTimeout(30 * 1000);
            conn.setConnectTimeout(30 * 1000);
            conn.setRequestMethod("GET");
            HttpUtil.setHeaders(header, conn);
            int respCode = conn.getResponseCode();
            String respBody = HttpUtil.readResponse(respCode, conn);
            if (respCode != HTTP_OK) {
                LOGGER.error("Error executing url={},queryString={},responseCode={},responseBody={}",
                        url, respCode, respBody);
                return false;
            } else {
                return "true".equals(respBody);
            }
        } catch (IOException e) {
            LOGGER.error("Error sending data url={}", url, e);
            return false;
        }
    }

}
