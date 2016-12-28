package net.tzolov.geode.jmx;

import javax.management.MBeanServerConnection;

import org.influxdb.InfluxDB;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
//@TestPropertySource(locations="classpath:test.application.properties")
public class JmxToGrafanaApplicationTests {

	@MockBean
	private InfluxDB influxDB;

	@MockBean
	private MBeanServerConnection jmxConnection;

	@Test
	public void contextLoads() {
	}
}
