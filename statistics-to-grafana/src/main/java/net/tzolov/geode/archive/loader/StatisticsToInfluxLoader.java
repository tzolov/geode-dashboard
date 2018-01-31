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

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.geode.internal.statistics.StatArchiveReader;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class StatisticsToInfluxLoader extends AbstractStatisticsTSDBLoader {

	private static final Logger LOG = LoggerFactory.getLogger(StatisticsToInfluxLoader.class);

	private int influxMeasurementBatchSize;

	private String influxRetentionPolicy;

	private String influxDatabaseName;

	private InfluxDB influxDB;

	private BatchPoints measurementBatch;

	private boolean skipZeroValuesTimeSeries;

	@Autowired
	public StatisticsToInfluxLoader(InfluxDB influxDb,
			@Value("${cleanDatabaseOnLoad}") boolean cleanDatabaseOnLoad,
			@Value("${influxRetentionPolicy}") String influxRetentionPolicy,
			@Value("${influxMeasurementBatchSize}") int influxMeasurementBatchSize,
			@Value("${influxDatabaseName}") String influxDatabaseName,
			@Value("${archiveFile}") File archiveFile,
			@Value("${geodeMemberName}") String geodeMemberName,
			@Value("${allowedStatTypes}") String[] allowStatTypes,
			@Value("${skipZeroValuesTimeSeries}") boolean skipZeroValuesTimeSeries) {

		super(cleanDatabaseOnLoad, archiveFile, geodeMemberName, allowStatTypes);
		this.skipZeroValuesTimeSeries = skipZeroValuesTimeSeries;

		Assert.notNull(influxDb, "Not null InfluxDB is required!");

		this.influxDB = influxDb;
		this.influxDatabaseName = influxDatabaseName;
		this.influxRetentionPolicy = influxRetentionPolicy;
		this.influxMeasurementBatchSize = influxMeasurementBatchSize;
	}

	@Override
	protected void doCreateEmptyDatabase() {
		LOG.info("(Re)create influxDB [" + influxDatabaseName + "]");
		influxDB.deleteDatabase(influxDatabaseName);
		influxDB.createDatabase(influxDatabaseName);
	}

	@Override
	protected void doPrepareMeasurementLoad() {
		measurementBatch = measurementsBatch();
	}

	@Override
	protected void doLoadMeasurement(String measurementName, String measurementType,
			int measurementSampleIndex, long measurementTimestamp, StatArchiveReader.StatValue[] measurementFields) {

		Builder measurement = Point.measurement(measurementName)
				.tag("type", measurementType)
				.time(measurementTimestamp, TimeUnit.MILLISECONDS);

		boolean pointsFound = false;

		for (StatArchiveReader.StatValue measurementField : measurementFields) {

			if (!skipZeroValuesTimeSeries || !allValuesAreZero(measurementField)) {
				pointsFound = true;
				measurement.addField(
						getMeasurementFieldName(measurementField),
						getMeasurementFieldValue(measurementField, measurementSampleIndex));
			}
		}

		if (pointsFound) {
			measurementBatch.point(measurement.build());
		}

		if (measurementSampleIndex % influxMeasurementBatchSize == 0) {
			influxDB.write(measurementBatch);
			measurementBatch = measurementsBatch();
			System.out.print(".");
		}
	}

	@Override
	protected void doCompleteMeasurementLoad() {
		// Write the remaining measurements in the batch
		if (measurementBatch.getPoints().size() > 0) {
			influxDB.write(measurementBatch);
		}
		measurementBatch = null;
		System.out.println();
	}

	private BatchPoints measurementsBatch() {
		BatchPoints batchPoints = BatchPoints
				.database(influxDatabaseName)
				.tag("async", "false")
				.tag("archiveMember", geodeMemberName)
				.retentionPolicy(influxRetentionPolicy)
				.consistency(ConsistencyLevel.ALL)
				.build();
		return batchPoints;
	}
}
