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

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * Created by tzoloc on 12/21/16.
 */
public class JmxUtils {

//	public static void main(String[] args) {
//		MBeanServerConnection mBeanServerConnection = jmxConnection("localhost", "1199");
//		for (String a :
//				attributeNames(mBeanServerConnection, "GemFire:service=Region,name=/itemRegion,type=Distributed",
//						new PrimitiveTypesFilter())) {
//			System.out.println(a);
//		}
//	}

	public static String[] attributeNames(MBeanServerConnection connection,
			String objectName, MBeanAttributeInfoFilter attributeFilter) {

		try {
			Builder<String> builder = ImmutableList.builder();
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
