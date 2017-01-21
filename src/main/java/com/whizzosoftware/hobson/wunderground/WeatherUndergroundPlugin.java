/*
 *******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.wunderground;

import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceType;
import com.whizzosoftware.hobson.api.event.EventHandler;
import com.whizzosoftware.hobson.api.event.plugin.PluginConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import com.whizzosoftware.hobson.api.plugin.http.AbstractHttpClientPlugin;
import com.whizzosoftware.hobson.api.plugin.http.HttpRequest;
import com.whizzosoftware.hobson.api.plugin.http.HttpResponse;
import com.whizzosoftware.hobson.api.property.PropertyConstraintType;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.api.variable.DeviceVariableContext;
import com.whizzosoftware.hobson.api.variable.DeviceVariableState;
import com.whizzosoftware.hobson.api.variable.VariableConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * A plugin that will send the weather-related variables of a particular device to Weather Underground as PWS data.
 *
 * @author Dan Noguerol
 */
public class WeatherUndergroundPlugin extends AbstractHttpClientPlugin implements HttpChannel {
    private static final Logger logger = LoggerFactory.getLogger(WeatherUndergroundPlugin.class);

    private static final long VAR_EXPIRE_TIME_MS = 600000;

    private DeviceContext deviceContext;
    private String pwsId;
    private String pwsPassword;
    private HttpChannel httpChannel;
    private Map<String,Long> lastVariableUpdate = new HashMap<>();
    private boolean pendingRequest;

    public WeatherUndergroundPlugin(String pluginId, String version, String description) {
        super(pluginId, version, description);
        this.httpChannel = this;
    }

    public WeatherUndergroundPlugin(String pluginId, String version, String description, HttpChannel httpChannel) {
        super(pluginId, version, description);
        this.httpChannel = httpChannel;
    }

    @Override
    protected TypedProperty[] getConfigurationPropertyTypes() {
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

    @EventHandler
    public void onPluginConfigurationUpdate(PluginConfigurationUpdateEvent event) {
        processConfig(event.getConfiguration());
    }

    @Override
    public void onRefresh() {
        onRefresh(System.currentTimeMillis());
    }

    void onRefresh(long now) {
        if (!pendingRequest) {
            if (deviceContext != null && pwsId != null && pwsPassword != null) {
                try {
                    StringBuilder url = new StringBuilder("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=")
                            .append(pwsId).append("&PASSWORD=").append(URLEncoder.encode(pwsPassword, "UTF8")).append("&dateutc=now");

                    boolean hasVariables = false;

                    DeviceVariableContext dvctx = DeviceVariableContext.create(deviceContext, VariableConstants.BAROMETRIC_PRESSURE_INHG);
                    if (hasDeviceVariableState(dvctx)) {
                        hasVariables = appendVariableToURL(getDeviceVariableState(dvctx), url, now);
                    }
                    dvctx = DeviceVariableContext.create(deviceContext, VariableConstants.DEW_PT_F);
                    if (hasDeviceVariableState(dvctx)) {
                        hasVariables = appendVariableToURL(getDeviceVariableState(dvctx), url, now) || hasVariables;
                    }
                    dvctx = DeviceVariableContext.create(deviceContext, VariableConstants.OUTDOOR_TEMP_F);
                    if (hasDeviceVariableState(dvctx)) {
                        hasVariables = appendVariableToURL(getDeviceVariableState(dvctx), url, now) || hasVariables;
                    }
                    dvctx = DeviceVariableContext.create(deviceContext, VariableConstants.OUTDOOR_RELATIVE_HUMIDITY);
                    if (hasDeviceVariableState(dvctx)) {
                        hasVariables = appendVariableToURL(getDeviceVariableState(dvctx), url, now) || hasVariables;
                    }
                    dvctx = DeviceVariableContext.create(deviceContext, VariableConstants.WIND_DIRECTION_DEGREES);
                    if (hasDeviceVariableState(dvctx)) {
                        hasVariables = appendVariableToURL(getDeviceVariableState(dvctx), url, now) || hasVariables;
                    }
                    dvctx = DeviceVariableContext.create(deviceContext, VariableConstants.WIND_SPEED_MPH);
                    if (hasDeviceVariableState(dvctx)) {
                        hasVariables = appendVariableToURL(getDeviceVariableState(dvctx), url, now) || hasVariables;
                    }

                    if (hasVariables) {
                        logger.debug("Calling update URL: {}", url.toString());
                        try {
                            httpChannel.sendHttpRequest(new URI(url.toString()), HttpRequest.Method.GET, null, null);
                            pendingRequest = true;
                        } catch (URISyntaxException e) {
                            logger.error("Error creating update URL", e);
                        }
                    } else {
                        logger.debug("No variable updates available; bypassing update");
                    }
                } catch (UnsupportedEncodingException uee) {
                    logger.error("Unable to create Weather Underground URL", uee);
                } catch (HobsonNotFoundException nfe) {
                    logger.warn("Unable to locate weather station device: {}; not sending any data this interval. The weather station plugin may still be starting up. If this condition persists there is a problem.", deviceContext);
                }
            }
        } else {
            logger.debug("A previous request is still pending; bypassing update");
        }
    }

    @Override
    public void onHttpResponse(HttpResponse response, Object context) {
        clearPendingRequest();
        try {
            String s = response.getBody();
            if (response.getStatusCode() == 200 && s != null && s.startsWith("success")) {
                logger.debug("Update successful");
            } else {
                logger.error("Failed to send update ({}): {}", response.getStatusCode(), response);
            }
        } catch (IOException e) {
            logger.error("Error processing HTTP response", e);
        }
    }

    @Override
    public void onHttpRequestFailure(Throwable cause, Object context) {
        clearPendingRequest();
        logger.error("Error calling update URL", cause);
    }

    void setPwsId(String pwsId) {
        this.pwsId = pwsId;
    }

    void setPwsPassword(String pwsPassword) {
        this.pwsPassword = pwsPassword;
    }

    void setDeviceContext(DeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    boolean hasPendingRequest() {
        return pendingRequest;
    }

    void clearPendingRequest() {
        pendingRequest = false;
    }

    boolean appendVariableToURL(DeviceVariableState v, StringBuilder url, long now) throws UnsupportedEncodingException {
        if (v != null && v.getContext().getName() != null && v.getValue() != null && !isVariableStale(v, now)) {
            if ((!lastVariableUpdate.containsKey(v.getContext().getName()) || now > lastVariableUpdate.get(v.getContext().getName()))) {
                String queryParam = getQueryParameterForVariable(v.getContext().getName());
                if (queryParam != null) {
                    url.append("&").append(queryParam).append("=").append(URLEncoder.encode(v.getValue().toString(), "UTF8"));
                    setLastVariableUpdate(v.getContext().getName(), v.getLastUpdate());
                    return true;
                }
            } else {
                logger.error("Detected stale variable: {}", v.getContext().getName());
            }
        }
        return false;
    }

    private void processConfig(PropertyContainer config) {
        setDeviceContext((DeviceContext)config.getPropertyValue("device"));
        setPwsId((String)config.getPropertyValue("pwsId"));
        setPwsPassword((String)config.getPropertyValue("pwsPassword"));

        if (deviceContext != null && pwsId != null && pwsId.trim().length() > 0 && pwsPassword != null && pwsPassword.trim().length() > 0) {
            setStatus(PluginStatus.running());
        } else {
            setStatus(PluginStatus.notConfigured(""));
        }
    }

    private String getQueryParameterForVariable(String varName) {
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

    private void setLastVariableUpdate(String varName, Long time) {
        if (time != null) {
            lastVariableUpdate.put(varName, time);
        }
    }

    private boolean isVariableStale(DeviceVariableState v, long now) {
        return (v.getLastUpdate() != null && now - v.getLastUpdate() >= VAR_EXPIRE_TIME_MS);
    }
}
