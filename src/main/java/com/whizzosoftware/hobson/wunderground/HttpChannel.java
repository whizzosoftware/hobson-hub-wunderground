/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.wunderground;

import com.whizzosoftware.hobson.api.plugin.http.HttpRequest;

import java.net.URI;
import java.util.Map;

/**
 * An interface for sending HTTP requests. Used for unit testing purposes.
 *
 * @author Dan Noguerol
 */
public interface HttpChannel {
    void sendHttpRequest(URI uri, HttpRequest.Method method, Map<String, String> headers, Object context);
}
