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
package net.tzolov.geode.jmx;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.ConsistencyLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gemstone.gemfire.management.DistributedSystemMXBean;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.googlecode.jmxtrans.JmxTransformer;
import com.googlecode.jmxtrans.cli.JmxTransConfiguration;
import com.googlecode.jmxtrans.guice.JmxTransModule;
import com.googlecode.jmxtrans.model.JmxProcess;
import com.googlecode.jmxtrans.model.Query;
import com.googlecode.jmxtrans.model.ResultAttribute;
import com.googlecode.jmxtrans.model.Server;
import com.googlecode.jmxtrans.model.Server.Builder;
import com.googlecode.jmxtrans.model.ServerFixtures;
import com.googlecode.jmxtrans.model.output.InfluxDbWriter;
import net.tzolov.geode.jmx.util.JmxTransformerDynamic;

@Service
public class JmxInfluxLoader {

	private static final Logger log = LoggerFactory.getLogger(JmxInfluxLoader.class);

	public static final String GEM_FIRE_SERVICE_SYSTEM_TYPE_DISTRIBUTED = "GemFire:service=System,type=Distributed";
	private static ImmutableSet<ResultAttribute> resultAttributesToWriteAsTags = ImmutableSet.of();

	private final MBeanServerConnection jmxConnection;
	private final InfluxDB influxDB;
	private DistributedSystemMXBean distributedSystemMXBean;
	private String mbeanHostName;
	private String mbeanPort;
	private String influxDatabaseName;

	private String cronExpression;

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
			@Value("${influxDatabaseName}") String influxDatabaseName,
			@Value("${cronExpression}") String cronExpression) {
		this.influxDB = influxDB;
		this.jmxConnection = jmxConnection;
		this.cleanDatabaseOnStart = cleanDatabaseOnStart;
		this.influxRetentionPolicy = influxRetentionPolicy;
		this.mbeanHostName = mbeanHostName;
		this.mbeanPort = mbeanPort;
		this.influxDatabaseName = influxDatabaseName;
		this.cronExpression = cronExpression;

		log.info("cronExpression: " + cronExpression);

		if (this.cleanDatabaseOnStart) {
			influxDB.deleteDatabase(influxDatabaseName);
			influxDB.createDatabase(this.influxDatabaseName);
		}
	}

	public void start() throws Exception {

		JmxProcess process = new JmxProcess(createServer());
		Injector injector = JmxTransModule.createInjector(new JmxTransConfiguration());
		transformer = injector.getInstance(JmxTransformerDynamic.class);
		transformer.executeStandalone(process);

		addNotificationListener(
				(notification, handback) -> {
					switch (notification.getType()) {
						case "gemfire.distributedsystem.cache.member.departed":
						case "gemfire.distributedsystem.cache.member.joined":
						case "gemfire.distributedsystem.cache.region.created":
						case "gemfire.distributedsystem.cache.region.closed]": {
							try {
								log.info("Reload Geode JMX definitions on event:" + notification.getType());
								JmxInfluxLoader.this.jmxSourceInitialize();
							}
							catch (Exception e) {
								log.error("Failed to reload the Geode JMXdefintiti", e);
							}
							break;
						}
					}
				});

	}

	// "0 0/1 * * * ?" - every minute
	private Server createServer() {
		Builder serverBuilder = Server.builder()
				.setHost(mbeanHostName)
				.setPort(mbeanPort)
				.setAlias("GeodeServers")
				.setCronExpression(cronExpression)
				.setPool(ServerFixtures.createPool());

		for (String member : getDistributedSystemMXBean().listMembers()) {
			serverBuilder.addQuery(memberQuery(influxDB, jmxConnection, member));
		}

		for (String region : getDistributedSystemMXBean().listRegions()) {
			serverBuilder.addQuery(regionQuery(influxDB, jmxConnection, region));
		}

		serverBuilder.addQuery(clusterQuery(influxDB, jmxConnection));

		return serverBuilder.build();
	}

	public void jmxSourceInitialize() throws Exception {
		if (transformer != null) {
			((JmxTransformerDynamic)transformer).reloadGeodeJmxMBeans(ImmutableList.of(createServer()));
		}
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
				.addAttr(attributeNames(jmxConnection, objectName, new PrimitiveTypesFilter()))
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
				log.error("", e);
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
		catch (Exception e) {
			log.error("Failed to register JMX notification listener: " +listener, e);
		}
	}


	private String[] attributeNames(MBeanServerConnection connection,
			String objectName, MBeanAttributeInfoFilter attributeFilter) {

		try {
			ImmutableList.Builder<String> builder = ImmutableList.builder();
			for (MBeanAttributeInfo attr : connection.getMBeanInfo(new ObjectName(objectName)).getAttributes()) {
				if (!attributeFilter.filter(attr)) {
					builder.add(attr.getName());
				}
			}
			ImmutableList<String> names = builder.build();
			return names.toArray(new String[names.size()]);
		}
		catch (Exception ex) {
			throw new RuntimeException((ex));
		}
	}

	public interface MBeanAttributeInfoFilter {
		boolean filter(MBeanAttributeInfo attributeInfo);
	}

	public static class PrimitiveTypesFilter implements MBeanAttributeInfoFilter {
		@Override
		public boolean filter(MBeanAttributeInfo attributeInfo) {
			switch (attributeInfo.getType()) {
				case "java.lang.String":
				case "long":
				case "float":
				case "boolean":
				case "double":
				case "int":
					return false;
				default:
					return true;
			}
		}
	}
}
