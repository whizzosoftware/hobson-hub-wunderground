package com.whizzosoftware.hobson.wunderground;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import com.whizzosoftware.hobson.api.plugin.http.AbstractHttpClientPlugin;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.api.variable.HobsonVariable;
import com.whizzosoftware.hobson.api.variable.VariableConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

public class WeatherUndergroundPlugin extends AbstractHttpClientPlugin {
    private static final Logger logger = LoggerFactory.getLogger(WeatherUndergroundPlugin.class);

    private DeviceContext deviceContext;
    private String pwsId;
    private String pwsPassword;
    private boolean hasPendingRequest;

    public WeatherUndergroundPlugin(String pluginId) {
        super(pluginId);
    }

    @Override
    protected TypedProperty[] createSupportedProperties() {
        return new TypedProperty[] {
            new TypedProperty("device", "Device", "The device reporting the weather data", TypedProperty.Type.DEVICE),
            new TypedProperty("pwsId", "PWS ID", "The Personal Weather Station ID", TypedProperty.Type.STRING),
            new TypedProperty("pwsPassword", "Password", "The Personal Weather Station password", TypedProperty.Type.SECURE_STRING)
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
        if (deviceContext != null && pwsId != null && pwsPassword != null) {
            try {
                StringBuilder url = new StringBuilder("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=")
                        .append(pwsId).append("&PASSWORD=").append(URLEncoder.encode(pwsPassword, "UTF8")).append("&dateutc=now");

                boolean hasVariables = false;

                if (hasDeviceVariable(deviceContext, VariableConstants.BAROMETRIC_PRESSURE_INHG)) {
                    HobsonVariable v = getDeviceVariable(deviceContext, VariableConstants.BAROMETRIC_PRESSURE_INHG);
                    url.append("&baromin=").append(URLEncoder.encode(v.getValue().toString(), "UTF8"));
                    hasVariables = true;
                }

                if (hasDeviceVariable(deviceContext, VariableConstants.DEW_PT_F)) {
                    HobsonVariable v = getDeviceVariable(deviceContext, VariableConstants.DEW_PT_F);
                    url.append("&dewptf=").append(URLEncoder.encode(v.getValue().toString(), "UTF8"));
                    hasVariables = true;
                }

                if (hasDeviceVariable(deviceContext, VariableConstants.OUTDOOR_TEMP_F)) {
                    HobsonVariable v = getDeviceVariable(deviceContext, VariableConstants.OUTDOOR_TEMP_F);
                    url.append("&tempf=").append(URLEncoder.encode(v.getValue().toString(), "UTF8"));
                    hasVariables = true;
                }

                if (hasDeviceVariable(deviceContext, VariableConstants.OUTDOOR_RELATIVE_HUMIDITY)) {
                    HobsonVariable v = getDeviceVariable(deviceContext, VariableConstants.OUTDOOR_RELATIVE_HUMIDITY);
                    url.append("&humidity=").append(URLEncoder.encode(v.getValue().toString(), "UTF8"));
                    hasVariables = true;
                }

                if (hasDeviceVariable(deviceContext, VariableConstants.WIND_DIRECTION_DEGREES)) {
                    HobsonVariable v = getDeviceVariable(deviceContext, VariableConstants.WIND_DIRECTION_DEGREES);
                    url.append("&winddir=").append(URLEncoder.encode(v.getValue().toString(), "UTF8"));
                    hasVariables = true;
                }

                if (hasDeviceVariable(deviceContext, VariableConstants.WIND_SPEED_MPH)) {
                    HobsonVariable v = getDeviceVariable(deviceContext, VariableConstants.WIND_SPEED_MPH);
                    url.append("&windspeedmph=").append(URLEncoder.encode(v.getValue().toString(), "UTF8"));
                    hasVariables = true;
                }

                if (hasVariables) {
                    if (!hasPendingRequest) {
                        logger.debug("Calling update URL: {}", url.toString());
                        try {
                            sendHttpGetRequest(new URI(url.toString()), null, null);
                            hasPendingRequest = true;
                        } catch (URISyntaxException e) {
                            logger.error("Error creating update URL", e);
                        }
                    } else {
                        logger.debug("A previous request is pending; bypassing update");
                    }
                } else {
                    logger.debug("No variables available; bypassing update");
                }
            } catch (UnsupportedEncodingException uee) {
                logger.error("Unable to create wunderground URL", uee);
            }
        }
    }

    @Override
    protected void onHttpResponse(int statusCode, List<Map.Entry<String, String>> headers, String response, Object context) {
        hasPendingRequest = false;
        if (statusCode == 200 && response != null && response.startsWith("success")) {
            logger.debug("Update successful");
        } else {
            logger.error("Failed to send update ({}): {}", statusCode, response);
        }
    }

    @Override
    protected void onHttpRequestFailure(Throwable cause, Object context) {
        hasPendingRequest = false;
        logger.error("Error calling update URL", cause);
    }

    protected void processConfig(PropertyContainer config) {
        String device = (String)config.getPropertyValue("device");
        if (device != null) {
            deviceContext = DeviceContext.create(device);
        }
        pwsId = (String)config.getPropertyValue("pwsId");
        pwsPassword = (String)config.getPropertyValue("pwsPassword");

        if (deviceContext != null && pwsId != null && pwsPassword != null) {
            setStatus(PluginStatus.running());
        } else {
            setStatus(PluginStatus.notConfigured(""));
        }
    }
}
