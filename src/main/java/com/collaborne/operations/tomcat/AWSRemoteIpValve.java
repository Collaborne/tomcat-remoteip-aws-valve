/**
 * Copyright © 2016 Collaborne B.V. (opensource@collaborne.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.collaborne.operations.tomcat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class AWSRemoteIpValve extends RemoteIpValve {
	/** URL to the published AWS IP ranges */
	private static final String DEFAULT_IP_RANGES_URL = "https://ip-ranges.amazonaws.com/ip-ranges.json";

	private static final Log log = LogFactory.getLog(AWSRemoteIpValve.class);

	private final ScheduledExecutorService updateScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "aws-ip-range-updater");
			t.setDaemon(true);
			return t;
		}
	});

	private String ipRangesUrl = DEFAULT_IP_RANGES_URL;
	private List<String> services = Arrays.asList(new String[] {"CLOUDFRONT"});
	/** Whether the initial update must succeed */
	private boolean requireInitialUpdateSuccess = true;
	
	private String lastETag;

	@Override
	protected void startInternal() throws LifecycleException {
		super.startInternal();

		// Do a blocking update now, so that even the first requests have a chance of being correct.
		// The 'requireInitialUpdateSuccess' setting can be used to allow this to fail.
		try {
			updateIpRanges();
		} catch (IOException e) {
			if (isRequireInitialUpdateSuccess()) {
				throw new LifecycleException("Cannot get initial AWS IP ranges", e);
			}

			log.warn("Cannot update AWS IP ranges", e);
		}

		// Schedule updates to happen every few seconds.
		updateScheduler.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				try {
					updateIpRanges();
				} catch (IOException e) {
					log.warn("Cannot update AWS IP ranges", e);
				}
			}
		}, 60, 60, TimeUnit.SECONDS);
	}

	public String getIpRangesUrl() {
		return ipRangesUrl;
	}

	public void setIpRangesUrl(String ipRangesUrl) {
		this.ipRangesUrl = ipRangesUrl;
	}

	public List<String> getServices() {
		return services;
	}

	public void setServices(String servicesString) {
		this.services = Arrays.stream(servicesString.split(",")).map(service -> service.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toList());
	}

	public boolean isRequireInitialUpdateSuccess() {
		return requireInitialUpdateSuccess;
	}

	public void setRequireInitialUpdateSuccess(boolean requireInitialUpdateSuccess) {
		this.requireInitialUpdateSuccess = requireInitialUpdateSuccess;
	}

	protected static void appendRangeRegularExpression(StringBuilder sb, String prefix) throws UnknownHostException {
		// Calculate first and last IP in the range
		int maskIndex = prefix.indexOf('/');
		int mask = Integer.parseInt(prefix.substring(maskIndex + 1));
		InetAddress network = Inet4Address.getByName(prefix.substring(0, maskIndex));
		byte[] firstAddress = network.getAddress();

		long lastIpLong = (
				((firstAddress[0] << 24) & 0xFF000000L) |
				((firstAddress[1] << 16) & 0x00FF0000L) |
				((firstAddress[2] <<  8) & 0x0000FF00L) |
				((firstAddress[3]      ) & 0x000000FFL)) | (0xFFFFFFFFL >> mask) ;
		byte[] lastAddress = new byte[] {
				(byte) ((lastIpLong & 0xFF000000L) >> 24),
				(byte) ((lastIpLong & 0x00FF0000L) >> 16),
				(byte) ((lastIpLong & 0x0000FF00L) >> 8),
				(byte)  (lastIpLong & 0x000000FFL) };

		// Build the RE
		// First: common prefix
		StringBuilder prefixBuilder = new StringBuilder();
		int i = 0;
		while (i < 4 && firstAddress[i] == lastAddress[i]) {
			prefixBuilder.append('(').append(firstAddress[i] & 0xFF).append(')');
			prefixBuilder.append("\\.");
			i++;
		}

		// Now, for next octet we may need to enumerate the possible values, and then compress them by their length
		int firstPossible = firstAddress[i] & 0xFF;
		int lastPossible = lastAddress[i] & 0xFF;
		if (firstPossible != 0 || lastPossible != 255) {
			prefixBuilder.append('(');
			for (int j = firstPossible; j <= lastPossible; j++) {
				// TODO: do the actual compression
				prefixBuilder.append(Integer.toString(j));
				if (j < lastPossible) {
					prefixBuilder.append('|');
				}
			}
			prefixBuilder.append(')');
			if (i < 3) {
				prefixBuilder.append("\\.");
			}
			i++;
		}

		// Finally, add the remaining octets as "any"
		while (i < 4) {
			prefixBuilder.append("([1-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])");
			if (i < 3) {
				prefixBuilder.append("\\.");
			}
			i++;
		}

		sb.append(prefixBuilder.toString());
	}

	protected void updateIpRanges() throws MalformedURLException, IOException {
		// Fetch the contents of ip-ranges.json, and find the ones for the services
		// Schedule the update to happen
		HttpURLConnection connection = (HttpURLConnection) new URL(ipRangesUrl).openConnection();
		connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(5));
		if (lastETag != null) {
			connection.addRequestProperty("If-None-Match", lastETag);
		}
		connection.connect();
		if (connection.getResponseCode() == 304) {
			// Good: Nothing has changed
			return;
		} else if (connection.getResponseCode() != 200) {
			// Bad: can't get the file, maybe the URL was changed, etc...
			throw new IOException("Failed to request current AWS IP ranges: " + connection.getResponseCode() + " " + connection.getResponseMessage());
		}
		lastETag = connection.getHeaderField("ETag");

		StringBuilder sb = new StringBuilder();
		try (JsonReader reader = Json.createReader(connection.getInputStream())) {
			JsonObject obj = reader.readObject();
			JsonArray prefixObjs = obj.getJsonArray("prefixes");
			boolean alternativeNeeded = false;
			for (JsonObject prefixObj : prefixObjs.getValuesAs(JsonObject.class)) {
				if (!getServices().contains(prefixObj.getString("service"))) {
					continue;
				}

				String prefix = prefixObj.getString("ip_prefix");
				if (alternativeNeeded) {
					sb.append('|');
				}
				appendRangeRegularExpression(sb, prefix);
				alternativeNeeded = true;
			}
		}

		String trustedProxies = sb.toString();
		updateTrustedProxies(trustedProxies);
	}

	/**
	 * Update the internal trusted proxies using {@link #setTrustedProxies(String)}.
	 *
	 * If the given value matches the current value the update is avoided.
	 *
	 * @param trustedProxies new trusted proxies
	 */
	// 'synchronized' so that we can guarantee that other threads see the update
	private synchronized void updateTrustedProxies(String trustedProxies) {
		if (trustedProxies.equals(getTrustedProxies())) {
			// Nothing has changed here
			return;
		}

		log.info("Updating trusted proxies: " + trustedProxies);
		setTrustedProxies(trustedProxies);
	}
}
