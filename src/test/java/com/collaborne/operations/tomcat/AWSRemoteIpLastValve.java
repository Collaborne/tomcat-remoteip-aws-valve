package com.collaborne.operations.tomcat;

import org.apache.catalina.Valve;

public class AWSRemoteIpLastValve  extends AWSRemoteIpValve {

    AWSRemoteIpLastValve(Valve nextValve) {
        this.next = nextValve;
    }

    @Override
    public Valve getNext() {
        return next;
    }
}
