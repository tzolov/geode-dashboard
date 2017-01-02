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
package net.tzolov.geode.jmx.util;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.apache.commons.lang.RandomStringUtils;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.googlecode.jmxtrans.ConfigurationParser;
import com.googlecode.jmxtrans.JmxTransformer;
import com.googlecode.jmxtrans.cli.JmxTransConfiguration;
import com.googlecode.jmxtrans.exceptions.LifecycleException;
import com.googlecode.jmxtrans.jobs.ServerJob;
import com.googlecode.jmxtrans.model.JmxProcess;
import com.googlecode.jmxtrans.model.OutputWriter;
import com.googlecode.jmxtrans.model.Query;
import com.googlecode.jmxtrans.model.Server;
import com.googlecode.jmxtrans.model.ValidationException;

/**
 * Override the original JmxTransformer class to allow dynamic statistics reconfiguration on JMX change event.
 */
public class JmxTransformerDynamic extends JmxTransformer {

	private static final Logger log = LoggerFactory.getLogger(JmxTransformerDynamic.class);

	private final Scheduler serverScheduler;

	private final JmxTransConfiguration configuration;

   private final Injector injector;

	@Nonnull private final ThreadPoolExecutor queryProcessorExecutor;

	@Nonnull private final ThreadPoolExecutor resultProcessorExecutor;

	@Nonnull private final ThreadLocalRandom random = ThreadLocalRandom.current();

	private ImmutableList<Server> masterServersList = ImmutableList.of();

	private volatile boolean isRunning = false;

	@Inject
	public JmxTransformerDynamic(
			Scheduler serverScheduler,
			JmxTransConfiguration configuration,
			ConfigurationParser configurationParser,
			Injector injector,
			@Nonnull @Named("queryProcessorExecutor") ThreadPoolExecutor queryProcessorExecutor,
			@Nonnull @Named("resultProcessorExecutor") ThreadPoolExecutor resultProcessorExecutor) {

		super(serverScheduler, configuration, configurationParser, injector, queryProcessorExecutor, resultProcessorExecutor);

		this.serverScheduler = serverScheduler;
		this.configuration = configuration;
		this.injector = injector;
		this.queryProcessorExecutor = queryProcessorExecutor;
		this.resultProcessorExecutor = resultProcessorExecutor;
	}

	public void reloadGeodeJmxMBeans(ImmutableList<Server> servers) throws Exception {
		Thread.sleep(1000);
		this.deleteAllJobs();
		this.masterServersList = servers;
		// process the servers into jobs
		this.processServersIntoJobs();
	}

	/**
	 * Shut down the output writers and clear the master server list
	 * Used both during shutdown and when re-reading config files
	 */
	private void stopWriterAndClearMasterServerList() {
		for (Server server : this.masterServersList) {
			for (OutputWriter writer : server.getOutputWriters()) {
				try {
					writer.close();
				}
				catch (LifecycleException ex) {
					log.error("Eror stopping writer: {}", writer);
				}
			}
			for (Query query : server.getQueries()) {
				for (OutputWriter writer : query.getOutputWriterInstances()) {
					try {
						writer.close();
						log.debug("Stopped writer: {} for query: {}", writer, query);
					}
					catch (LifecycleException ex) {
						log.error("Error stopping writer: {} for query: {}", writer, query, ex);
					}
				}
			}
		}
		this.masterServersList = ImmutableList.of();
	}

	/**
	 * Handy method which runs the JmxProcess
	 */
	public void executeStandalone(JmxProcess process) throws Exception {

		if (isRunning) {
			throw new LifecycleException("Process already started");
		}

		this.masterServersList = process.getServers();

		this.serverScheduler.start();

		this.processServersIntoJobs();

		// Ensure resources are free
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					JmxTransformerDynamic.this.stopServices();
				}
				catch (LifecycleException e) {
					e.printStackTrace();
				}
			}
		});

		isRunning = true;

		// Sleep for 10 seconds to wait for jobs to complete.
		// There should be a better way, but it seems that way isn't working
		// right now.
		Thread.sleep(MILLISECONDS.convert(10, SECONDS));
	}

	private void validateSetup(Server server, ImmutableSet<Query> queries) throws ValidationException {
		for (Query q : queries) {
			this.validateSetup(server, q);
		}
	}

	private void validateSetup(Server server, Query query) throws ValidationException {
		for (OutputWriter w : query.getOutputWriterInstances()) {
			injector.injectMembers(w);
			w.validateSetup(server, query);
		}
	}

	/**
	 * Processes all the Servers into Job's
	 * <p/>
	 * Needs to be called after processFiles()
	 */
	private void processServersIntoJobs() throws LifecycleException {
		for (Server server : this.masterServersList) {
			try {

				// need to inject the poolMap
				for (Query query : server.getQueries()) {
					for (OutputWriter writer : query.getOutputWriterInstances()) {
						writer.start();
					}
				}

				// Now validate the setup of each of the OutputWriter's per
				// query.
				this.validateSetup(server, server.getQueries());

				// Now schedule the jobs for execution.
				this.scheduleJob(server);
			}
			catch (ParseException ex) {
				throw new LifecycleException("Error parsing cron expression: " + server.getCronExpression(), ex);
			}
			catch (SchedulerException ex) {
				throw new LifecycleException("Error scheduling job for server: " + server, ex);
			}
			catch (ValidationException ex) {
				throw new LifecycleException("Error validating json setup for query", ex);
			}
		}
	}

	private void scheduleJob(Server server) throws ParseException, SchedulerException {

		String name = server.getHost() + ":" + server.getPort() + "-" + System.currentTimeMillis() + "-" + RandomStringUtils.randomNumeric(10);
		JobDetail jd = new JobDetail(name, "ServerJob", ServerJob.class);

		JobDataMap map = new JobDataMap();
		map.put(Server.class.getName(), server);
		jd.setJobDataMap(map);

		Trigger trigger;

		if ((server.getCronExpression() != null) && CronExpression.isValidExpression(server.getCronExpression())) {
			trigger = new CronTrigger();
			((CronTrigger) trigger).setCronExpression(server.getCronExpression());
			trigger.setName(server.getHost() + ":" + server.getPort() + "-" + Long.toString(System.currentTimeMillis()));
			trigger.setStartTime(computeSpreadStartDate(configuration.getRunPeriod()));
		}
		else {
			int runPeriod = configuration.getRunPeriod();
			if (server.getRunPeriodSeconds() != null) runPeriod = server.getRunPeriodSeconds();
			Trigger minuteTrigger = TriggerUtils.makeSecondlyTrigger(runPeriod);
			minuteTrigger.setName(server.getHost() + ":" + server.getPort() + "-" + Long.toString(System.currentTimeMillis()));
			minuteTrigger.setStartTime(computeSpreadStartDate(runPeriod));

			trigger = minuteTrigger;

			// TODO replace Quartz with a ScheduledExecutorService
		}

		serverScheduler.scheduleJob(jd, trigger);
		if (log.isDebugEnabled()) {
			log.debug("Scheduled job: " + jd.getName() + " for server: " + server);
		}
	}

	@VisibleForTesting
	Date computeSpreadStartDate(int runPeriod) {
		long spread = random.nextLong(MILLISECONDS.convert(runPeriod, SECONDS));
		return new Date(new Date().getTime() + spread);
	}

	private void deleteAllJobs() throws Exception {
		List<JobDetail> allJobs = new ArrayList<>();
		String[] jobGroups = serverScheduler.getJobGroupNames();
		for (String jobGroup : jobGroups) {
			String[] jobNames = serverScheduler.getJobNames(jobGroup);
			for (String jobName : jobNames) {
				allJobs.add(serverScheduler.getJobDetail(jobName, jobGroup));
			}
		}

		for (JobDetail jd : allJobs) {
			serverScheduler.deleteJob(jd.getName(), jd.getGroup());
			if (log.isDebugEnabled()) {
				log.debug("Deleted scheduled job: " + jd.getName() + " group: " + jd.getGroup());
			}
		}
	}

	// There is a sleep to work around a Quartz issue. The issue is marked to be
	// fixed, but will require further analysis. This should not be reported by
	// Findbugs, but as a more complex issue.
	private synchronized void stopServices() throws LifecycleException {
		try {
			// Shutdown the scheduler
			if (serverScheduler.isStarted()) {
				serverScheduler.shutdown(true);
				log.debug("Shutdown server scheduler");
				try {
					// FIXME: Quartz issue, need to sleep
					Thread.sleep(1500);
				}
				catch (InterruptedException e) {
					log.error(e.getMessage(), e);
					currentThread().interrupt();
				}
			}

			shutdownAndAwaitTermination(queryProcessorExecutor, 10, SECONDS);
			shutdownAndAwaitTermination(resultProcessorExecutor, 10, SECONDS);

			// Shutdown the outputwriters
			stopWriterAndClearMasterServerList();

		}
		catch (Exception e) {
			throw new LifecycleException(e);
		}
	}
}
