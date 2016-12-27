package net.tzolov.geode.archive.loader;

 import java.io.File;
import java.util.concurrent.TimeUnit;

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

import com.gemstone.gemfire.internal.StatArchiveReader.StatValue;

@Service
public class StatisticsToInfluxLoader extends AbstractStatisticsTSDBLoader {

	private static final Logger LOG = LoggerFactory.getLogger(StatisticsToInfluxLoader.class);

	private int influxMeasurementBatchSize;

	private String influxRetentionPolicy;

	private String influxDatabaseName;

	private InfluxDB influxDB;

	private BatchPoints measurementBatch;

	@Autowired
	public StatisticsToInfluxLoader(InfluxDB influxDb,
			@Value("${cleanDatabaseOnLoad}") boolean cleanDatabaseOnLoad,
			@Value("${influxRetentionPolicy}") String influxRetentionPolicy,
			@Value("${influxMeasurementBatchSize}") int influxMeasurementBatchSize,
			@Value("${influxDatabaseName}") String influxDatabaseName,
			@Value("${archiveFile}") File archiveFile,
			@Value("${geodeMemberName}") String geodeMemberName) {

		super(cleanDatabaseOnLoad, archiveFile, geodeMemberName);

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
			int measurementSampleIndex, long measurementTimestamp, StatValue[] measurementFields) {

		Builder measurement = Point.measurement(measurementName)
				.tag("type", measurementType)
				.time(measurementTimestamp, TimeUnit.MILLISECONDS);

		for (StatValue measurementField : measurementFields) {
			measurement.addField(
					getMeasurementFieldName(measurementField),
					getMeasurementFieldValue(measurementField, measurementSampleIndex));
		}
		measurementBatch.point(measurement.build());

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
