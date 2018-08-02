package com.collaborne.operations.tomcat;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Request;
import org.apache.catalina.core.StandardContext;

import java.util.*;

public class MockRequest extends Request {

    private HashMap<String, List<String>> headers = new HashMap<>();
    private String scheme = "http";
    private Context context = new StandardContext();

    MockRequest() {
        setCoyoteRequest(new org.apache.coyote.Request());
    }

    public MockRequest addHeader(String key, String value) {
        List<String> list = this.headers.get(key);
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(value);
        this.headers.put(key, list);
        return this;
    }

    @Override
    public String getHeader(String name) {
        List<String> values = this.headers.get(name);
        if (values == null) return null;
        return values.get(0);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> values = this.headers.get(name);
        return Collections.enumeration(values);
    }

    @Override
    public String getScheme() {
        return  scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public Context getContext() {
        return this.context;
    }

}
