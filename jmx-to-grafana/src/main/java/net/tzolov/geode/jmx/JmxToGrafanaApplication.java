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

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.LogLevel;
import org.influxdb.InfluxDBFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
public class JmxToGrafanaApplication implements CommandLineRunner {

	@Autowired
	private JmxInfluxLoader jmxInfluxLoader;

	public static void main(String[] args) {
		SpringApplication.run(JmxToGrafanaApplication.class, args);
	}

	@Bean
	public InfluxDB influxDB(
			@Value("${influxUrl}") String influxUrl,
			@Value("${influxUser}") String influxUser,
			@Value("${influxPassword}") String influxPassword) {
		InfluxDB influxDB = InfluxDBFactory.connect(influxUrl, influxUser, influxPassword);
		influxDB.setLogLevel(LogLevel.NONE);
		return influxDB;
	}

	@Bean
	public MBeanServerConnection jmxConnection(
			@Value("${mbeanHostName}") String mbeanHostName,
			@Value("${mbeanPort}") String mbeanPort) {

		try {
			String mbeanServerUrl = "service:jmx:rmi:///jndi/rmi://" + mbeanHostName.trim() + ":" + mbeanPort.trim() + "/jmxrmi";
			JMXServiceURL url = new JMXServiceURL(mbeanServerUrl);

			JMXConnector jmxConnector = JMXConnectorFactory.connect(url, null);
			return jmxConnector.getMBeanServerConnection();
		}
		catch (Exception ex) {
			throw new RuntimeException((ex));
		}
	}

	@Override
	public void run(String... strings) throws Exception {
		jmxInfluxLoader.start();
	}
}
