package net.tzolov.geode.archive.loader;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;

import com.gemstone.gemfire.internal.StatArchiveReader;
import com.gemstone.gemfire.internal.StatArchiveReader.StatValue;

/**
 * Common parent for all TSDB data loaders.
 * https://github.com/apache/geode/blob/develop/geode-core/src/test/java/org/apache/geode/internal/statistics/StatArchiveWriterReaderIntegrationTest.java
 */
public abstract class AbstractStatisticsTSDBLoader {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractStatisticsTSDBLoader.class);

	protected File archiveFileName;

	protected String geodeMemberName;

	protected boolean cleanDatabaseOnLoad;

	/**
	 * @param cleanDatabaseOnLoad If true the target TSDB is created or recreate.
	 * @param archiveFile The Apache Geode (GemFire) statistics archive file
	 * @param geodeMemberName Unique name used to distinct the statistics in this
	 * 			archiveFile from the archive files in the other members.
	 */
	public AbstractStatisticsTSDBLoader(boolean cleanDatabaseOnLoad, File archiveFile, String geodeMemberName) {

		Assert.notNull(archiveFile, "Not null archiveFile is required!");
		Assert.hasText(geodeMemberName, "Not empty geodeMemberName is required!");

		this.archiveFileName = archiveFile;
		this.geodeMemberName = geodeMemberName;
		this.cleanDatabaseOnLoad = cleanDatabaseOnLoad;
	}

	public void load() throws IOException {

		if (cleanDatabaseOnLoad) {
			doCreateEmptyDatabase();
		}

		final StatArchiveReader reader =
				new StatArchiveReader(new File[] {archiveFileName}, null, false);

		for (Object r : reader.getResourceInstList()) {

			final StatArchiveReader.ResourceInst ri = (StatArchiveReader.ResourceInst) r;

			String measurementName = ri.getType().getName() + ":" + ri.getName();

			LOG.info("\nMeasurement: " + measurementName
					+ ", Samples#: " + ri.getSampleCount()
					+ ", Fields#: " + ri.getStatValues().length);

			doPrepareMeasurementLoad();

			for (int measurementIndex = 0; measurementIndex < ri.getSampleCount(); measurementIndex++) {

				StatValue[] measurementFields = ri.getStatValues();

				long measurementTimestamp = measurementFields[0].getRawAbsoluteTimeStamps()[measurementIndex];

				doLoadMeasurement(measurementName, ri.getType().getName(), measurementIndex,
						measurementTimestamp, measurementFields);
			}

			doCompleteMeasurementLoad();
		}
	}

	abstract protected void doCreateEmptyDatabase();

	abstract protected void doPrepareMeasurementLoad();

	abstract protected void doLoadMeasurement(String measurementName, String measurementType, int measurementSampleIndex,
			long measurementTimestamp, StatValue[] measurementFields);

	abstract protected void doCompleteMeasurementLoad();

	/**
	 * Computes a name for the provided measurement field.
	 * @param measurementField field to compute the measurement name for.
	 * @return Returns a measurement name for the provided measurementField
	 */
	public String getMeasurementFieldName(StatValue measurementField) {
		return measurementField.getDescriptor().getName();
	}

	/**
	 * Extracts the Double value for measurement field and sample index.
	 * @param measurementField Measurement field to extract value from.
	 * @param measurementSampleIndex Field contains list of all samples. Use the index to extract the value for the specified one.
	 * @return Returns the value for the provided field and sample index.
	 */
	public double getMeasurementFieldValue(StatValue measurementField, int measurementSampleIndex) {
		return measurementField.getRawSnapshots()[measurementSampleIndex];
	}
}
