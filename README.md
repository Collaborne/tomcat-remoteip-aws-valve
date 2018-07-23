tomcat-remoteip-aws-valve [![Build Status](https://travis-ci.org/Collaborne/tomcat-remoteip-aws-valve.svg?branch=master)](https://travis-ci.org/Collaborne/tomcat-remoteip-aws-valve)
=========================

The tomcat-remoteip-aws-valve is a (almost 1:1) replacement for the existing RemoteIpValve delivered with Tomcat. Instead of using a hard-coded list of trusted proxies
it generates the list based on the [published IP ranges of AWS](http://docs.aws.amazon.com/general/latest/gr/aws-ip-ranges.html).

By default only cloudfront is included, but is possible via configuration to change the services.

The valve loads the IP ranges once on startup, and checks for updates every minute by default. The update frequency can be configured by setting the "updateInterval" (in seconds).

Installation
------------

The tomcat-remoteip-aws-valve is published to Maven Central, so the following dependency will add it to your project.
~~~~
<dependency>
    <groupId>com.collaborne.operations</groupId>
    <artifactId>tomcat-remoteip-aws-valve</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
</dependency>
~~~~

The jar file and its dependencies (`javax.json:javax.json-api:1.0` and an implementation of JSR-353 such as `org.glassfish:javax.json`) need to be
copied into a place that Tomcat uses as classpath. This path can be configured using the `common.loader` property in `${catalina.base}/conf/catalina.properties`.

Configuration
-------------

1. Enable the valve in `${catalina.base}/conf/server.xml`:
   ~~~~
   <Valve className="com.collaborne.operations.tomcat.AWSRemoteIpValve"
          services="CLOUDFRONT"
          requestAttributesEnabled="true"
          remoteIpHeader="x-forwarded-for" protocolHeader="x-forwarded-proto"
          requireInitialUpdateSuccess="true"
          updateInterval="60"
          />
   ~~~~

2. For using the remote IP addresses in the `AccessLogValve` additionally enable the `requestAttributesEnabled` attribute of the `AccessLogValve`.
   ~~~~
   <Valve className="org.apache.catalina.valves.AccessLogValve" directory="logs"
          requestAttributesEnabled="true"
          prefix="localhost_access_log" suffix=".txt"
          pattern="%h %l %u %t &quot;%r&quot; %s %b" />
   ~~~~

License
-------

    This software is licensed under the Apache 2 license, quoted below.

    Copyright 2016-2016 Collaborne B.V. <http://github.com/Collaborne/>

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy of
    the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
    License for the specific language governing permissions and limitations under
    the License.
