/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tzolov.geode.jmx.service;

import java.io.IOException;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.ConsistencyLevel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gemstone.gemfire.management.DistributedSystemMXBean;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.googlecode.jmxtrans.JmxTransformer;
import com.googlecode.jmxtrans.cli.JmxTransConfiguration;
import com.googlecode.jmxtrans.exceptions.LifecycleException;
import com.googlecode.jmxtrans.guice.JmxTransModule;
import com.googlecode.jmxtrans.model.JmxProcess;
import com.googlecode.jmxtrans.model.Query;
import com.googlecode.jmxtrans.model.ResultAttribute;
import com.googlecode.jmxtrans.model.Server;
import com.googlecode.jmxtrans.model.Server.Builder;
import com.googlecode.jmxtrans.model.ServerFixtures;
import com.googlecode.jmxtrans.model.output.InfluxDbWriter;
import net.tzolov.geode.jmx.service.JmxUtils.PrimitiveTypesFilter;

@Service
public class JmxInfluxLoader {

	public static final String GEM_FIRE_SERVICE_SYSTEM_TYPE_DISTRIBUTED = "GemFire:service=System,type=Distributed";
	private static ImmutableSet<ResultAttribute> resultAttributesToWriteAsTags = ImmutableSet.of();

	private final MBeanServerConnection jmxConnection;
	private final InfluxDB influxDB;
	private DistributedSystemMXBean distributedSystemMXBean;
	private String mbeanHostName;
	private String mbeanPort;
	private String influxDatabaseName;
	private boolean cleanDatabaseOnStart;
	private String influxRetentionPolicy;
	private JmxTransformer transformer;

	@Autowired
	public JmxInfluxLoader(InfluxDB influxDB,
			MBeanServerConnection jmxConnection,
			@Value("${cleanDatabaseOnStart}") boolean cleanDatabaseOnStart,
			@Value("${influxRetentionPolicy}") String influxRetentionPolicy,
			@Value("${mbeanHostName}") String mbeanHostName,
			@Value("${mbeanPort}") String mbeanPort,
			@Value("${influxDatabaseName}") String influxDatabaseName) {
		this.influxDB = influxDB;
		this.jmxConnection = jmxConnection;
		this.cleanDatabaseOnStart = cleanDatabaseOnStart;
		this.influxRetentionPolicy = influxRetentionPolicy;
		this.mbeanHostName = mbeanHostName;
		this.mbeanPort = mbeanPort;
		this.influxDatabaseName = influxDatabaseName;


		if (this.cleanDatabaseOnStart) {
			influxDB.deleteDatabase(influxDatabaseName);
			influxDB.createDatabase(this.influxDatabaseName);
		}
	}


//	public static void main(String[] args) throws Exception {
//		JmxInfluxLoader jmxInfluxLoader = new JmxInfluxLoader("localhost", "1199",
//				"http://localhost:8086", "jmxDb");
//
//		jmxInfluxLoader.start();
//	}

	public void start() throws Exception {

		Builder serverBuilder = Server.builder()
				.setHost(mbeanHostName)
				.setPort(mbeanPort)
				.setAlias("GeodeServers")
				.setPool(ServerFixtures.createPool());

		for (String member : getDistributedSystemMXBean().listMembers()) {
			serverBuilder.addQuery(memberQuery(influxDB, jmxConnection, member));
		}

		for (String region : getDistributedSystemMXBean().listRegions()) {
			serverBuilder.addQuery(regionQuery(influxDB, jmxConnection, region));
		}

		serverBuilder.addQuery(clusterQuery(influxDB, jmxConnection));

		JmxProcess process = new JmxProcess(serverBuilder.build());

		Injector injector = JmxTransModule.createInjector(new JmxTransConfiguration());
		transformer = injector.getInstance(JmxTransformer.class);
		transformer.executeStandalone(process);

		addNotificationListener(
				(notification, handback) -> {
					System.out.println("event: " + notification);
					switch (notification.getType()) {
						case "gemfire.distributedsystem.cache.member.departed":
						case "gemfire.distributedsystem.cache.member.joined":
						case "gemfire.distributedsystem.cache.region.created":
						case "gemfire.distributedsystem.cache.region.closed]": {
							try {
								JmxInfluxLoader.this.restart();
							}
							catch (Exception e) {
								e.printStackTrace();
							}
							break;
						}
					}
				});

	}

	public void stop() throws LifecycleException {
		if (transformer != null) {
			transformer.stop();
		}
	}

	public void restart() throws Exception {
		this.stop();
		this.start();
	}

	private Query clusterQuery(InfluxDB influxDB, MBeanServerConnection jmxConnection) {

		ImmutableMap<String, String> tags = ImmutableMap.of();

		String objectName = "GemFire:service=System,type=Distributed";

		return createQuery(influxDB, jmxConnection, tags, objectName, "Distributed");
	}

	private Query memberQuery(InfluxDB influxDB, MBeanServerConnection jmxConnection, String memberName) {

		ImmutableMap<String, String> tags = ImmutableMap.<String, String>builder().put("member", memberName).build();

		String objectName = "GemFire:type=Member,member=" + memberName;

		return createQuery(influxDB, jmxConnection, tags, objectName, "Member");
	}

	private Query regionQuery(InfluxDB influxDB, MBeanServerConnection jmxConnection, String regionName) {

		ImmutableMap<String, String> tags = ImmutableMap.<String, String>builder().put("region", regionName).build();

		String objectName = "GemFire:service=Region,name=/" + regionName.trim() + ",type=Distributed";

		return createQuery(influxDB, jmxConnection, tags, objectName, "Region");
	}

	private Query createQuery(InfluxDB influxDB, MBeanServerConnection jmxConnection,
			ImmutableMap<String, String> tags, String objectName, String measurementName) {

		InfluxDbWriter influxDbWriter = new InfluxDbWriter(influxDB,
				influxDatabaseName, ConsistencyLevel.ALL,
				influxRetentionPolicy,
				tags,
				resultAttributesToWriteAsTags,
				false
		);

		Query query = Query.builder()
				.setObj(objectName)
				.addAttr(JmxUtils.attributeNames(jmxConnection, objectName, new PrimitiveTypesFilter()))
				.setResultAlias(measurementName)
				.addOutputWriters(ImmutableSet.of(influxDbWriter))
				.build();

		return query;
	}

	private DistributedSystemMXBean getDistributedSystemMXBean() {
		if (distributedSystemMXBean == null) {

			try {
				distributedSystemMXBean =
						MBeanServerInvocationHandler.newProxyInstance(
								jmxConnection,
								new ObjectName(GEM_FIRE_SERVICE_SYSTEM_TYPE_DISTRIBUTED),
								DistributedSystemMXBean.class,
								false);
			}
			catch (MalformedObjectNameException e) {
				e.printStackTrace();
			}
		}

		return distributedSystemMXBean;
	}

	public void addNotificationListener(NotificationListener listener) {
		try {
			this.jmxConnection.addNotificationListener(
					new ObjectName(GEM_FIRE_SERVICE_SYSTEM_TYPE_DISTRIBUTED),
					listener,
					null,
					null);
		}
		catch (InstanceNotFoundException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		catch (MalformedObjectNameException e) {
			e.printStackTrace();
		}
	}
}
