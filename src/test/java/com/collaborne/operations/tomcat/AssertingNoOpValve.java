package com.collaborne.operations.tomcat;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Assertions;

import javax.servlet.ServletException;
import java.io.IOException;

public class AssertingNoOpValve extends ValveBase {

    private String expectedRemoteAddr;

    public AssertingNoOpValve(String expectedRemoteAddr) {
        super();
        this.expectedRemoteAddr = expectedRemoteAddr;
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        Assertions.assertEquals(expectedRemoteAddr, request.getRemoteAddr());
    }
}
