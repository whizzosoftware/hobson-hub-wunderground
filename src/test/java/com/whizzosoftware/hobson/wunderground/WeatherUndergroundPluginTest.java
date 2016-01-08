/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.wunderground;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.variable.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherUndergroundPluginTest {
    @Test
    public void testURLUpdate() {
        long now = System.currentTimeMillis();

        DeviceContext deviceContext = DeviceContext.createLocal("plugin1", "device1");
        MockHttpChannel channel = new MockHttpChannel();
        MockVariableManager variableManager = new MockVariableManager();

        WeatherUndergroundPlugin plugin = new WeatherUndergroundPlugin("plugin2", channel);
        plugin.setVariableManager(variableManager);
        plugin.setDeviceContext(deviceContext);

        // refresh with no variables
        plugin.onRefresh(now);
        assertEquals(0, channel.getURICount());

        // add a temperature variable but there's no pws ID or password
        variableManager.publishVariable(VariableContext.create(deviceContext, VariableConstants.OUTDOOR_TEMP_F), 72.5, HobsonVariable.Mask.READ_ONLY);

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
        ((MutableHobsonVariable)variableManager.getPublishedDeviceVariable(deviceContext, VariableConstants.OUTDOOR_TEMP_F)).setValue(73.5);
        plugin.onRefresh(now + 500);
        assertEquals(0, channel.getURICount());

        // clear pending request and update variable again
        plugin.clearPendingRequest();
        assertFalse(plugin.hasPendingRequest());
        ((MutableHobsonVariable)variableManager.getPublishedDeviceVariable(deviceContext, VariableConstants.OUTDOOR_TEMP_F)).setValue(74.5);
        plugin.onRefresh(now + 600);
        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&tempf=74.5", channel.getURI(0).toASCIIString());

        // add a second variable
        variableManager.publishVariable(VariableContext.create(deviceContext, VariableConstants.BAROMETRIC_PRESSURE_INHG), 5, HobsonVariable.Mask.READ_ONLY);
        plugin.clearPendingRequest();
        assertFalse(plugin.hasPendingRequest());
        channel.clear();
        assertEquals(0, channel.getURICount());
        ((MutableHobsonVariable)variableManager.getPublishedDeviceVariable(deviceContext, VariableConstants.OUTDOOR_TEMP_F)).setValue(74.5);
        plugin.onRefresh(now + 700);
        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&baromin=5&tempf=74.5", channel.getURI(0).toASCIIString());
    }

    @Test
    public void testFullVariableUpdate() {
        long now = System.currentTimeMillis();

        DeviceContext deviceContext = DeviceContext.createLocal("plugin1", "device1");
        MockHttpChannel channel = new MockHttpChannel();
        MockVariableManager variableManager = new MockVariableManager();

        WeatherUndergroundPlugin plugin = new WeatherUndergroundPlugin("plugin2", channel);
        plugin.setVariableManager(variableManager);
        plugin.setDeviceContext(deviceContext);
        plugin.setPwsId("foo");
        plugin.setPwsPassword("bar");

        variableManager.publishVariable(VariableContext.create(deviceContext, VariableConstants.BAROMETRIC_PRESSURE_INHG), 5, HobsonVariable.Mask.READ_ONLY);
        variableManager.publishVariable(VariableContext.create(deviceContext, VariableConstants.DEW_PT_F), 6, HobsonVariable.Mask.READ_ONLY);
        variableManager.publishVariable(VariableContext.create(deviceContext, VariableConstants.OUTDOOR_TEMP_F), 7, HobsonVariable.Mask.READ_ONLY);
        variableManager.publishVariable(VariableContext.create(deviceContext, VariableConstants.OUTDOOR_RELATIVE_HUMIDITY), 8, HobsonVariable.Mask.READ_ONLY);
        variableManager.publishVariable(VariableContext.create(deviceContext, VariableConstants.WIND_DIRECTION_DEGREES), 9, HobsonVariable.Mask.READ_ONLY);
        variableManager.publishVariable(VariableContext.create(deviceContext, VariableConstants.WIND_SPEED_MPH), 10, HobsonVariable.Mask.READ_ONLY);

        plugin.onRefresh(now);

        assertTrue(plugin.hasPendingRequest());
        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&baromin=5&dewptf=6&tempf=7&humidity=8&winddir=9&windspeedmph=10", channel.getURI(0).toASCIIString());
    }

    @Test
    public void testAppendVariableToURL() throws Exception {
        WeatherUndergroundPlugin plugin = new WeatherUndergroundPlugin("plugin");
        MutableHobsonVariable v = new MutableHobsonVariable(VariableContext.createLocal("plugin", "device1", VariableConstants.OUTDOOR_TEMP_F), HobsonVariable.Mask.READ_ONLY, null, null);
        StringBuilder url = new StringBuilder();

        assertNull(v.getLastUpdate());

        long now = System.currentTimeMillis();

        // test null value
        assertFalse(plugin.appendVariableToURL(v, url, now));

        // test valid value
        v.setValue("32");
        assertTrue(plugin.appendVariableToURL(v, url, now + 2));
        assertEquals("&tempf=32", url.toString());

        // test invalid variable name
        v = new MutableHobsonVariable(VariableContext.createLocal("plugin", "device1", "foo"), HobsonVariable.Mask.READ_ONLY, 32, null);
        assertFalse(plugin.appendVariableToURL(v, url, now + 3));
    }

    @Test
    public void testExpiredVariableUpdate() throws Exception {
        long now = System.currentTimeMillis();
        MockHttpChannel channel = new MockHttpChannel();

        DeviceContext dctx = DeviceContext.createLocal("plugin", "device1");

        MockVariableManager variableManager = new MockVariableManager();
        variableManager.publishVariable(VariableContext.create(dctx, VariableConstants.OUTDOOR_TEMP_F), 41.2, HobsonVariable.Mask.READ_ONLY);

        WeatherUndergroundPlugin plugin = new WeatherUndergroundPlugin("plugin", channel);
        plugin.setVariableManager(variableManager);
        plugin.setDeviceContext(dctx);
        plugin.setPwsId("foo");
        plugin.setPwsPassword("bar");

        plugin.onRefresh(now);

        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&tempf=41.2", channel.getURI(0).toASCIIString());
        assertTrue(plugin.hasPendingRequest());
        plugin.onHttpResponse(200, null, "success", null);
        assertFalse(plugin.hasPendingRequest());

        plugin.onRefresh(now + 601000);
        assertEquals(1, channel.getURICount());

        ((MutableHobsonVariable)variableManager.getPublishedDeviceVariable(dctx, VariableConstants.OUTDOOR_TEMP_F)).setValue(42);
        ((MutableHobsonVariable)variableManager.getPublishedDeviceVariable(dctx, VariableConstants.OUTDOOR_TEMP_F)).setLastUpdate(now+70000);
        plugin.onRefresh(now + 602000);
        assertEquals(2, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&tempf=42", channel.getURI(1).toASCIIString());
    }
}
