/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.wunderground;

import com.whizzosoftware.hobson.api.plugin.http.Cookie;
import com.whizzosoftware.hobson.api.plugin.http.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.util.Collection;
import java.util.Map;

public class MockHttpResponse implements HttpResponse {
    private int statusCode;
    private String body;

    MockHttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String getStatusText() {
        return null;
    }

    @Override
    public boolean hasHeader(String s) {
        return false;
    }

    @Override
    public Collection<String> getHeader(String s) {
        return null;
    }

    @Override
    public Map<String, Collection<String>> getHeaders() {
        return null;
    }

    @Override
    public boolean hasCookies() {
        return false;
    }

    @Override
    public Collection<Cookie> getCookies() {
        return null;
    }

    @Override
    public String getBody() throws IOException {
        return body;
    }

    @Override
    public InputStream getBodyAsStream() throws IOException {
        return new StringBufferInputStream(body);
    }
}
