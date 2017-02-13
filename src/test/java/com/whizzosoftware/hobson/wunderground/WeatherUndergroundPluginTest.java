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

import com.whizzosoftware.hobson.api.device.DeviceType;
import com.whizzosoftware.hobson.api.device.MockDeviceManager;
import com.whizzosoftware.hobson.api.device.MockDeviceProxy;
import com.whizzosoftware.hobson.api.event.MockEventManager;
import com.whizzosoftware.hobson.api.plugin.MockHobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.http.MockHttpResponse;
import com.whizzosoftware.hobson.api.variable.*;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherUndergroundPluginTest {
    private MockHttpChannel channel;
    private MockDeviceProxy wsDevice;
    private WeatherUndergroundPlugin plugin;

    @Before
    public void setUp() {
        MockDeviceManager dm = new MockDeviceManager();
        MockEventManager em = new MockEventManager();
        channel = new MockHttpChannel();

        // create weather station plugin / device
        MockHobsonPlugin wsPlugin = new MockHobsonPlugin("plugin1", "1.0", "Weather Station Plugin");
        wsPlugin.setDeviceManager(dm);
        wsPlugin.setEventManager(em);
        wsDevice = new MockDeviceProxy(wsPlugin, "device1", DeviceType.WEATHER_STATION);
        dm.publishDevice(wsDevice, null, null);

        // create weather underground plugin
        plugin = new WeatherUndergroundPlugin("plugin2", null, null, channel);
        plugin.setDeviceManager(dm);
        plugin.setEventManager(em);
        plugin.setDeviceContext(wsDevice.getContext());
    }

    @Test
    public void testURLUpdate() {
        long now = System.currentTimeMillis();

        // refresh with no variables
        plugin.onRefresh(now);
        assertEquals(0, channel.getURICount());

        // add a temperature variable but there's no pws ID or password
        wsDevice.publishVariables(new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), VariableConstants.OUTDOOR_TEMP_F), VariableMask.READ_ONLY, 72.5, now + 50));

        // refresh
        plugin.onRefresh(now + 100);
        assertEquals(0, channel.getURICount());
        assertFalse(plugin.hasPendingRequest());

        // set the pws ID but not password
        plugin.setPwsId("foo");
        plugin.onRefresh(now + 200);
        assertEquals(0, channel.getURICount());
        assertFalse(plugin.hasPendingRequest());

        // set the pws password
        plugin.setPwsPassword("bar");
        plugin.onRefresh(now + 300);
        assertTrue(plugin.hasPendingRequest());
        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&tempf=72.5", channel.getURI(0).toASCIIString());

        // refresh again without the variable having been updated
        channel.clear();
        plugin.onRefresh(now + 400);
        assertEquals(0, channel.getURICount());

        // update the variable but nothing should be sent because of previous pending request
        wsDevice.setVariableValue(VariableConstants.OUTDOOR_TEMP_F, 73.5, now + 450);
        plugin.onRefresh(now + 500);
        assertEquals(0, channel.getURICount());

        // clear pending request and update variable again
        plugin.clearPendingRequest();
        assertFalse(plugin.hasPendingRequest());
        wsDevice.setVariableValue(VariableConstants.OUTDOOR_TEMP_F, 74.5, now + 550);
        plugin.onRefresh(now + 600);
        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&tempf=74.5", channel.getURI(0).toASCIIString());

        // add a second variable
        wsDevice.publishVariables(new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), VariableConstants.BAROMETRIC_PRESSURE_INHG), VariableMask.READ_ONLY, 5, now + 650));
        plugin.clearPendingRequest();
        assertFalse(plugin.hasPendingRequest());
        channel.clear();
        assertEquals(0, channel.getURICount());
        wsDevice.setVariableValue(VariableConstants.OUTDOOR_TEMP_F, 74.5, now + 650);
        plugin.onRefresh(now + 700);
        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&baromin=5&tempf=74.5", channel.getURI(0).toASCIIString());
    }

    @Test
    public void testFullVariableUpdate() {
        long now = System.currentTimeMillis();

        plugin.setPwsId("foo");
        plugin.setPwsPassword("bar");

        wsDevice.publishVariables(
            new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), VariableConstants.BAROMETRIC_PRESSURE_INHG), VariableMask.READ_ONLY, 5, now),
            new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), VariableConstants.DEW_PT_F), VariableMask.READ_ONLY, 6, now),
            new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), VariableConstants.OUTDOOR_TEMP_F), VariableMask.READ_ONLY, 7, now),
            new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), VariableConstants.OUTDOOR_RELATIVE_HUMIDITY), VariableMask.READ_ONLY, 8, now),
            new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), VariableConstants.WIND_DIRECTION_DEGREES), VariableMask.READ_ONLY, 9, now),
            new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), VariableConstants.WIND_SPEED_MPH), VariableMask.READ_ONLY, 10, now)
        );

        plugin.onRefresh(now);

        assertTrue(plugin.hasPendingRequest());
        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&baromin=5&dewptf=6&tempf=7&humidity=8&winddir=9&windspeedmph=10", channel.getURI(0).toASCIIString());
    }

    @Test
    public void testAppendVariableToURL() throws Exception {
        long now = System.currentTimeMillis();
        StringBuilder url = new StringBuilder();

        // test invalid variable name
        wsDevice.publishVariables(new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), "foo"), VariableMask.READ_ONLY, 32, null));
        assertFalse(plugin.appendVariableToURL(wsDevice.getVariableState("foo"), url, now + 3));

        // test valid variable name with no value
        wsDevice.publishVariables(new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), VariableConstants.OUTDOOR_TEMP_F), VariableMask.READ_ONLY, null, null));
        assertNull(wsDevice.getVariableState(VariableConstants.OUTDOOR_TEMP_F).getLastUpdate());
        assertFalse(plugin.appendVariableToURL(wsDevice.getVariableState(VariableConstants.OUTDOOR_TEMP_F), url, now));

        // test valid variable name with value
        wsDevice.setVariableValue(VariableConstants.OUTDOOR_TEMP_F, 32, now + 1);
        assertTrue(plugin.appendVariableToURL(wsDevice.getVariableState(VariableConstants.OUTDOOR_TEMP_F), url, now + 2));
        assertEquals("&tempf=32", url.toString());

    }

    @Test
    public void testExpiredVariableUpdate() throws Exception {
        long now = System.currentTimeMillis();

        wsDevice.publishVariables(new DeviceProxyVariable(DeviceVariableContext.create(wsDevice.getContext(), VariableConstants.OUTDOOR_TEMP_F), VariableMask.READ_ONLY, 41.2, now));

        plugin.setPwsId("foo");
        plugin.setPwsPassword("bar");

        plugin.onRefresh(now);

        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&tempf=41.2", channel.getURI(0).toASCIIString());
        assertTrue(plugin.hasPendingRequest());
        plugin.onHttpResponse(new MockHttpResponse(200, "success"), null);
        assertFalse(plugin.hasPendingRequest());

        plugin.onRefresh(now + 601000);
        assertEquals(1, channel.getURICount());

        wsDevice.setVariableValue(VariableConstants.OUTDOOR_TEMP_F, 42, now + 70000);
        plugin.onRefresh(now + 602000);
        assertEquals(2, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&tempf=42", channel.getURI(1).toASCIIString());
    }
}
