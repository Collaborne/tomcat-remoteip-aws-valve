package com.collaborne.operations.tomcat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AWSRemoteIpValveTest {

    public static final int ITERATIONS = 100000;
    public static final String REMOTE_ADDR = "1.2.3.4";
    public static final String A_REAL_CLOUDFRONT_ADDRESS = "143.204.194.179";
    private static final String A_LOAD_BALANCER = "192.168.1.1";
    private static AWSRemoteIpValve valve = new AWSRemoteIpLastValve(new AssertingNoOpValve(REMOTE_ADDR));

    @BeforeAll
    public static void init() throws IOException {
        valve.updateIpRanges();
    }

    @Test
    public void testLatency() throws Exception {
        MockRequest request = createMockRequest();
        valve.invoke(request, null); // Get rid of overhead for the first request
        long t1 = System.currentTimeMillis();
        for(int i = 0; i< ITERATIONS; i++) {
            valve.invoke(request, null);
        }
        long t2 = System.currentTimeMillis();

        Long duration = t2 - t1;
        Float avgDuration = (duration.floatValue()/ITERATIONS);

        System.out.println("Duration: " + duration + "ms for " + ITERATIONS + " iterations");
        System.out.println("Average: " + avgDuration + "ms");

        assertTrue(avgDuration<0.01);
    }

    private MockRequest createMockRequest() {
        MockRequest request = new MockRequest();
        request.setRemoteAddr(A_LOAD_BALANCER);
        request.setRemoteHost(A_LOAD_BALANCER);
        request.setServerPort(8080);
        request.setScheme("https");
        request.addHeader("X-Forwarded-By", A_REAL_CLOUDFRONT_ADDRESS);
        request.addHeader("X-Forwarded-By", A_LOAD_BALANCER);
        request.addHeader("X-Forwarded-For", REMOTE_ADDR);
        return request;
    }

}