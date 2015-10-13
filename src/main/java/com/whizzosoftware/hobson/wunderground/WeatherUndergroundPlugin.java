/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.wunderground;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceType;
import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import com.whizzosoftware.hobson.api.plugin.http.AbstractHttpClientPlugin;
import com.whizzosoftware.hobson.api.property.PropertyConstraintType;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.api.property.TypedPropertyConstraint;
import com.whizzosoftware.hobson.api.variable.HobsonVariable;
import com.whizzosoftware.hobson.api.variable.VariableConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A plugin that will send the weather-related variables of a particular device to Weather Underground as PWS data.
 *
 * @author Dan Noguerol
 */
public class WeatherUndergroundPlugin extends AbstractHttpClientPlugin implements HttpChannel {
    private static final Logger logger = LoggerFactory.getLogger(WeatherUndergroundPlugin.class);

    private DeviceContext deviceContext;
    private String pwsId;
    private String pwsPassword;
    private HttpChannel httpChannel;
    private Map<String,Long> lastVariableUpdate = new HashMap<>();
    private boolean pendingRequest;

    public WeatherUndergroundPlugin(String pluginId) {
        super(pluginId);
        this.httpChannel = this;
    }

    public WeatherUndergroundPlugin(String pluginId, HttpChannel httpChannel) {
        super(pluginId);
        this.httpChannel = httpChannel;
    }

    @Override
    protected TypedProperty[] createSupportedProperties() {
        return new TypedProperty[] {
            new TypedProperty.Builder("device", "Device", "The device reporting the weather data", TypedProperty.Type.DEVICE).constraint(PropertyConstraintType.required, true).constraint(PropertyConstraintType.deviceType, DeviceType.WEATHER_STATION.toString()).build(),
            new TypedProperty.Builder("pwsId", "PWS ID", "The Personal Weather Station ID", TypedProperty.Type.STRING).constraint(PropertyConstraintType.required, true).build(),
            new TypedProperty.Builder("pwsPassword", "Password", "The Personal Weather Station password", TypedProperty.Type.SECURE_STRING).constraint(PropertyConstraintType.required, true).build()
        };
    }

    @Override
    public String getName() {
        return "Weather Underground";
    }

    @Override
    public long getRefreshInterval() {
        return 300;
    }

    @Override
    public void onStartup(PropertyContainer config) {
        processConfig(config);
    }

    @Override
    public void onShutdown() {
    }

    @Override
    public void onPluginConfigurationUpdate(PropertyContainer config) {
        processConfig(config);
    }

    @Override
    public void onRefresh() {
        onRefresh(System.currentTimeMillis());
    }

    protected void onRefresh(long now) {
        if (!pendingRequest) {
            if (deviceContext != null && pwsId != null && pwsPassword != null) {
                try {
                    StringBuilder url = new StringBuilder("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=")
                            .append(pwsId).append("&PASSWORD=").append(URLEncoder.encode(pwsPassword, "UTF8")).append("&dateutc=now");

                    boolean hasVariables = false;

                    if (hasDeviceVariable(deviceContext, VariableConstants.BAROMETRIC_PRESSURE_INHG)) {
                        hasVariables = appendVariableToURL(getDeviceVariable(deviceContext, VariableConstants.BAROMETRIC_PRESSURE_INHG), url, now) ? true : hasVariables;
                    }

                    if (hasDeviceVariable(deviceContext, VariableConstants.DEW_PT_F)) {
                        hasVariables = appendVariableToURL(getDeviceVariable(deviceContext, VariableConstants.DEW_PT_F), url, now) ? true : hasVariables;
                    }

                    if (hasDeviceVariable(deviceContext, VariableConstants.OUTDOOR_TEMP_F)) {
                        hasVariables = appendVariableToURL(getDeviceVariable(deviceContext, VariableConstants.OUTDOOR_TEMP_F), url, now) ? true : hasVariables;
                    }

                    if (hasDeviceVariable(deviceContext, VariableConstants.OUTDOOR_RELATIVE_HUMIDITY)) {
                        hasVariables = appendVariableToURL(getDeviceVariable(deviceContext, VariableConstants.OUTDOOR_RELATIVE_HUMIDITY), url, now) ? true : hasVariables;
                    }

                    if (hasDeviceVariable(deviceContext, VariableConstants.WIND_DIRECTION_DEGREES)) {
                        hasVariables = appendVariableToURL(getDeviceVariable(deviceContext, VariableConstants.WIND_DIRECTION_DEGREES), url, now) ? true : hasVariables;
                    }

                    if (hasDeviceVariable(deviceContext, VariableConstants.WIND_SPEED_MPH)) {
                        hasVariables = appendVariableToURL(getDeviceVariable(deviceContext, VariableConstants.WIND_SPEED_MPH), url, now) ? true : hasVariables;
                    }

                    if (hasVariables) {
                        logger.debug("Calling update URL: {}", url.toString());
                        try {
                            httpChannel.sendHttpGetRequest(new URI(url.toString()), null, null);
                            pendingRequest = true;
                        } catch (URISyntaxException e) {
                            logger.error("Error creating update URL", e);
                        }
                    } else {
                        logger.debug("No variables available; bypassing update");
                    }
                } catch (UnsupportedEncodingException uee) {
                    logger.error("Unable to create wunderground URL", uee);
                }
            }
        } else {
            logger.debug("A previous request is pending; bypassing update");
        }
    }

    @Override
    protected void onHttpResponse(int statusCode, List<Map.Entry<String, String>> headers, String response, Object context) {
        clearPendingRequest();
        if (statusCode == 200 && response != null && response.startsWith("success")) {
            logger.debug("Update successful");
        } else {
            logger.error("Failed to send update ({}): {}", statusCode, response);
        }
    }

    @Override
    protected void onHttpRequestFailure(Throwable cause, Object context) {
        clearPendingRequest();
        logger.error("Error calling update URL", cause);
    }

    protected void setPwsId(String pwsId) {
        this.pwsId = pwsId;
    }

    protected void setPwsPassword(String pwsPassword) {
        this.pwsPassword = pwsPassword;
    }

    protected DeviceContext getDeviceContext() {
        return deviceContext;
    }

    protected void setDeviceContext(DeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    protected void processConfig(PropertyContainer config) {
        setDeviceContext((DeviceContext)config.getPropertyValue("device"));
        pwsId = (String)config.getPropertyValue("pwsId");
        pwsPassword = (String)config.getPropertyValue("pwsPassword");

        if (deviceContext != null && pwsId != null && pwsId.trim().length() > 0 && pwsPassword != null && pwsPassword.trim().length() > 0) {
            setStatus(PluginStatus.running());
        } else {
            setStatus(PluginStatus.notConfigured(""));
        }
    }

    protected boolean appendVariableToURL(HobsonVariable v, StringBuilder url, long now) throws UnsupportedEncodingException {
        if (!lastVariableUpdate.containsKey(v.getName()) || now > lastVariableUpdate.get(v.getName())) {
            url.append("&").append(getQueryParameterForVariable(v.getName())).append("=").append(URLEncoder.encode(v.getValue().toString(), "UTF8"));
            setLastVariableUpdate(v.getName(), v.getLastUpdate());
            return true;
        } else {
            logger.error("Detected stale variable: {}", v.getName());
        }
        return false;
    }

    protected String getQueryParameterForVariable(String varName) {
        switch (varName) {
            case VariableConstants.BAROMETRIC_PRESSURE_INHG:
                return "baromin";
            case VariableConstants.DEW_PT_F:
                return "dewptf";
            case VariableConstants.OUTDOOR_TEMP_F:
                return "tempf";
            case VariableConstants.OUTDOOR_RELATIVE_HUMIDITY:
                return "humidity";
            case VariableConstants.WIND_DIRECTION_DEGREES:
                return "winddir";
            case VariableConstants.WIND_SPEED_MPH:
                return "windspeedmph";
            default:
                return null;
        }
    }

    protected void setLastVariableUpdate(String varName, long time) {
        lastVariableUpdate.put(varName, time);
    }

    protected boolean hasPendingRequest() {
        return pendingRequest;
    }

    protected void clearPendingRequest() {
        pendingRequest = false;
    }
}
