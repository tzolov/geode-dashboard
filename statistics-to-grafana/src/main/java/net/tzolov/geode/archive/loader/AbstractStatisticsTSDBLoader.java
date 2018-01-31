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
import java.io.IOException;
import java.util.IdentityHashMap;

import com.google.common.collect.ImmutableSet;
import org.apache.geode.internal.statistics.StatArchiveReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;

/**
 * Common parent for all TSDB data loaders.
 * https://github.com/apache/geode/blob/develop/geode-core/src/test/java/org/apache/geode/internal/statistics/StatArchiveWriterReaderIntegrationTest.java
 */
public abstract class AbstractStatisticsTSDBLoader {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractStatisticsTSDBLoader.class);

	protected File archiveFileName;

	protected String geodeMemberName;

	protected boolean cleanDatabaseOnLoad;

	private IdentityHashMap<StatArchiveReader.StatValue, double[]> statValueCache = new IdentityHashMap<>();

	private IdentityHashMap<StatArchiveReader.StatValue, Boolean> statValueEmpty = new IdentityHashMap<>();

	private StatArchiveReader.ValueFilter[] statFilters;

	/**
	 * @param cleanDatabaseOnLoad If true the target TSDB is created or recreate.
	 * @param archiveFile The Apache Geode (GemFire) statistics archive file
	 * @param geodeMemberName Unique name used to distinct the statistics in this
	 * @param allowStatTypes List of Statistic Type Names to import. If empty all statistic is imported
	 */
	public AbstractStatisticsTSDBLoader(boolean cleanDatabaseOnLoad, File archiveFile, String geodeMemberName,
			String[] allowStatTypes) {

		Assert.notNull(archiveFile, "Not null archiveFile is required!");
		Assert.hasText(geodeMemberName, "Not empty geodeMemberName is required!");

		this.archiveFileName = archiveFile;
		this.geodeMemberName = geodeMemberName;
		this.cleanDatabaseOnLoad = cleanDatabaseOnLoad;

		if (allowStatTypes != null && allowStatTypes.length > 0) {
			statFilters = new StatArchiveReader.ValueFilter[] { new StatFilter(allowStatTypes) };
		}
	}

	public void load() throws IOException {

		if (cleanDatabaseOnLoad) {
			doCreateEmptyDatabase();
		}

		statValueCache.clear();
		statValueEmpty.clear();

		final StatArchiveReader reader =
				new StatArchiveReader(new File[] { archiveFileName }, statFilters, false);

		for (Object r : reader.getResourceInstList()) {

			final StatArchiveReader.ResourceInst ri = (StatArchiveReader.ResourceInst) r;

			if (statFilters != null && statFilters.length > 0 && !statFilters[0].typeMatches(ri.getType().getName())) {
				// Filter out measurement types not in the allowed types list
				continue;
			}

			String measurementName = ri.getType().getName() + ":" + ri.getName();

			LOG.info("Measurement [" + measurementName
					+ "], Samples: " + ri.getSampleCount()
					+ ", Fields: " + ri.getStatValues().length);

			doPrepareMeasurementLoad();

			for (int measurementIndex = 0; measurementIndex < ri.getSampleCount(); measurementIndex++) {

				StatArchiveReader.StatValue[] measurementFields = ri.getStatValues();

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
			long measurementTimestamp, StatArchiveReader.StatValue[] measurementFields);

	abstract protected void doCompleteMeasurementLoad();

	/**
	 * Computes a name for the provided measurement field.
	 * @param measurementField field to compute the measurement name for.
	 * @return Returns a measurement name for the provided measurementField
	 */
	public String getMeasurementFieldName(StatArchiveReader.StatValue measurementField) {
		return measurementField.getDescriptor().getName();
	}

	/**
	 * Extracts the Double value for measurement field and sample index.
	 * @param measurementField Measurement field to extract value from.
	 * @param measurementSampleIndex Field contains list of all samples. Use the index to extract the value for the specified one.
	 * @return Returns the value for the provided field and sample index.
	 */
	public double getMeasurementFieldValue(StatArchiveReader.StatValue measurementField, int measurementSampleIndex) {
		return getCachedSeries(measurementField)[measurementSampleIndex];
	}

	public boolean allValuesAreZero(StatArchiveReader.StatValue measurementField) {

		if (!statValueEmpty.containsKey(measurementField)) {
			statValueEmpty.put(measurementField, areZeros(getCachedSeries(measurementField)));
		}

		return statValueEmpty.get(measurementField);
	}

	private boolean areZeros(double[] values) {
		for (double d : values) {
			if (d != 0.0d) {
				return false;
			}
		}
		return true;
	}

	private double[] getCachedSeries(StatArchiveReader.StatValue measurementField) {
		if (!statValueCache.containsKey(measurementField)) {
//			System.out.println("Cache statistics values for : " + measurementField.getDescriptor().getName());
			statValueCache.put(measurementField, measurementField.getRawSnapshots());
		}
		return statValueCache.get(measurementField);
	}

	public static class StatFilter implements StatArchiveReader.ValueFilter {
		private final ImmutableSet<String> statTypes;

		StatFilter(String[] allowedStatTypes) {
			statTypes = ImmutableSet.copyOf(allowedStatTypes);
		}

		@Override
		public boolean archiveMatches(File file) {
			return true;
		}

		@Override
		public boolean typeMatches(String s) {
			return statTypes.contains(s);
		}

		@Override
		public boolean statMatches(String s) {
			return true;
		}

		@Override
		public boolean instanceMatches(String s, long l) {
			return true;
		}
	}
}
