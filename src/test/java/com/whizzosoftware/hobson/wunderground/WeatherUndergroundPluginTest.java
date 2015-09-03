/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.wunderground;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.variable.HobsonVariable;
import com.whizzosoftware.hobson.api.variable.MockHobsonVariable;
import com.whizzosoftware.hobson.api.variable.MockVariableManager;
import com.whizzosoftware.hobson.api.variable.VariableConstants;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherUndergroundPluginTest {
    @Test
    public void testURLUpdate() {
        DeviceContext deviceContext = DeviceContext.createLocal("plugin1", "device1");
        MockHttpChannel channel = new MockHttpChannel();
        MockVariableManager variableManager = new MockVariableManager();

        WeatherUndergroundPlugin plugin = new WeatherUndergroundPlugin("plugin2", channel);
        plugin.setVariableManager(variableManager);
        plugin.setDeviceContext(deviceContext);

        // refresh with no variables
        plugin.onRefresh();
        assertEquals(0, channel.getURICount());

        // add a temperature variable but there's no pws ID or password
        variableManager.publishDeviceVariable(deviceContext, VariableConstants.OUTDOOR_TEMP_F, 72.5, HobsonVariable.Mask.READ_ONLY);

        // refresh
        plugin.onRefresh();
        assertEquals(0, channel.getURICount());
        assertFalse(plugin.hasPendingRequest());

        // set the pws ID but not password
        plugin.setPwsId("foo");
        plugin.onRefresh();
        assertEquals(0, channel.getURICount());
        assertFalse(plugin.hasPendingRequest());

        // set the pws password
        plugin.setPwsPassword("bar");
        plugin.onRefresh();
        assertTrue(plugin.hasPendingRequest());
        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&tempf=72.5", channel.getURI(0).toASCIIString());

        // refresh again without the variable having been updated
        channel.clear();
        plugin.onRefresh();
        assertEquals(0, channel.getURICount());

        // update the variable but nothing should be sent because of previous pending request
        ((MockHobsonVariable)variableManager.getPublishedDeviceVariable(deviceContext, VariableConstants.OUTDOOR_TEMP_F)).setValue(73.5);
        plugin.onRefresh();
        assertEquals(0, channel.getURICount());

        // clear pending request and update variable again
        plugin.clearPendingRequest();
        assertFalse(plugin.hasPendingRequest());
        ((MockHobsonVariable)variableManager.getPublishedDeviceVariable(deviceContext, VariableConstants.OUTDOOR_TEMP_F)).setValue(74.5);
        plugin.onRefresh();
        assertEquals(1, channel.getURICount());
        assertEquals("http://weatherstation.wunderground.com/weatherstation/updateweatherstation.php?ID=foo&PASSWORD=bar&dateutc=now&tempf=74.5", channel.getURI(0).toASCIIString());
    }
}
