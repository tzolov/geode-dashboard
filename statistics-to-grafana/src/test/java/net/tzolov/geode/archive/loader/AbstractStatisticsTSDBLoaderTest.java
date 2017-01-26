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
package net.tzolov.geode.archive.loader;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.gemstone.gemfire.internal.StatArchiveReader.StatValue;

public class AbstractStatisticsTSDBLoaderTest {

	@Test
	public void test1() throws IOException {

		TestMeasurementLoader testLoader = new TestMeasurementLoader(false,
				new File("src/test/resources/myStatisticsArchiveFile.gfs"), "SERVER666");

		testLoader.load();

		assertEquals("SERVER666", testLoader.geodeMemberName);
		assertEquals("src/test/resources/myStatisticsArchiveFile.gfs", testLoader.archiveFileName.getPath());
		assertEquals(0, testLoader.createEmptyDatabaseCount);
		assertEquals(30, testLoader.prepareMeasurementLoad);
		assertEquals(30, testLoader.completeMeasurementLoad);
		assertEquals(29, testLoader.measurements.size());

		assertEquals(EXPECTED_MEASUREMENT_NAMES, testLoader.measurements.keySet());
	}

	HashSet<String> EXPECTED_MEASUREMENT_NAMES = new HashSet<>(Arrays.asList("VMMemoryPoolStats:PS Eden Space-Heap memory",
			"CachePerfStats:cachePerfStats", "CacheServerStats:192.168.0.12-/127.0.0.1:40405", "VMGCStats:PS MarkSweep",
			"FunctionServiceStatistics:FunctionExecution", "VMGCStats:PS Scavenge",
			"CachePerfStats:RegionStats-managementRegionStats", "ResourceManagerStats:ResourceManagerStats",
			"CachePerfStats:RegionStats-itemRegion", "VMMemoryUsageStats:vmNonHeapMemoryStats",
			"LocatorStats:192.168.0.12-localhost/127.0.0.1:10334", "CachePerfStats:RegionStats-partition-ticketRegion",
			"DistributionStats:distributionStats", "VMMemoryUsageStats:vmHeapMemoryStats",
			"VMMemoryPoolStats:PS Old Gen-Heap memory", "CacheClientNotifierStatistics:cacheClientNotifierStats",
			"VMMemoryPoolStats:Code Cache-Non-heap memory", "VMMemoryPoolStats:Metaspace-Non-heap memory",
			"VMMemoryPoolStats:Compressed Class Space-Non-heap memory",
			"VMMemoryPoolStats:PS Survivor Space-Heap memory",
			"CacheClientProxyStatistics:cacheClientProxyStats-id_192.168.0.12(ClientWorker:8434:loner):52585:dadff53b:" +
					"ClientWorker_at_127.0.0.1:52589", "CachePerfStats:RegionStats-partitionMetaData",
			"CacheClientProxyStatistics:cacheClientProxyStats-id_192.168.0.12(ClientWorker:8411:loner):52406:69a0f43b:" +
					"ClientWorker_at_127.0.0.1:52410", "VMStats:vmStats", "StatSampler:statSampler",
			"PartitionedRegionStats:/ticketRegion",
			"ClientSubscriptionStats:ClientSubscriptionStats-_gfe_non_durable_client_with_id_192.168.0.12(ClientWorker:" +
					"8434:loner):52585:dadff53b:ClientWorker_2_queue", "DLockStats:dlockStats", "ClientSubscriptionStats:" +
					"ClientSubscriptionStats-_gfe_non_durable_client_with_id_192.168.0.12(ClientWorker:8411:loner):52406:" +
					"69a0f43b:ClientWorker_2_queue"));

	private static class MeasurementRecord {
		String measurementName;

		String measurementType;

		int measurementSampleIndex;

		long measurementTimestamp;

		StatValue[] measurementFields;

		public MeasurementRecord(String measurementName, String measurementType, int measurementSampleIndex, long measurementTimestamp, StatValue[] measurementFields) {
			this.measurementName = measurementName;
			this.measurementType = measurementType;
			this.measurementSampleIndex = measurementSampleIndex;
			this.measurementTimestamp = measurementTimestamp;
			this.measurementFields = measurementFields;
		}
	}

	private static class TestMeasurementLoader extends AbstractStatisticsTSDBLoader {

		public int createEmptyDatabaseCount = 0;

		public int completeMeasurementLoad = 0;

		public int prepareMeasurementLoad = 0;

		public Map<String, List<MeasurementRecord>> measurements = new HashMap<>();

		public TestMeasurementLoader(boolean cleanDatabaseOnLoad, File archiveFile, String geodeMemberName) {
			super(cleanDatabaseOnLoad, archiveFile, geodeMemberName, new String[0]);
		}

		@Override
		protected void doCreateEmptyDatabase() {
			createEmptyDatabaseCount++;
		}

		@Override
		protected void doPrepareMeasurementLoad() {
			prepareMeasurementLoad++;
		}

		@Override
		protected void doLoadMeasurement(String measurementName,
				String measurementType, int measurementSampleIndex,
				long measurementTimestamp, StatValue[] measurementFields) {

			if (!measurements.containsKey(measurementName)) {
				measurements.put(measurementName, new ArrayList<>());
			}
			measurements.get(measurementName).add(new MeasurementRecord(measurementName, measurementType,
					measurementSampleIndex, measurementTimestamp, measurementFields));
		}

		@Override
		protected void doCompleteMeasurementLoad() {
			completeMeasurementLoad++;
		}
	}
}
