# Apache Geode real-time JMX Metrics Plot with Grafana

[<img align="left" src="http://img.youtube.com/vi/e2UlWm1w2yY/0.jpg" alt="zeppelin-view" hspace="10" width="130"></img>](https://www.youtube.com/watch?v=e2UlWm1w2yY)
[Apache Geode](http://geode.apache.org/) uses a federated [Open MBean](http://geode.apache.org/docs/guide/managing/management/mbean_architecture.html)
architecture to manage and monitor all members of the distributed system. Your Java classes interact with a single
MBeanServer that aggregates MBeans from other local and remote members. Using this strategy gives you a consolidated,
single-agent view of the distributed system.					
One can use generic JMX clients to monitor or manage the Geode distributed system by using JMX compliant tools such
as [JConsole](http://geode.apache.org/docs/guide/managing/management/mbeans_jconsole.html)
and [Geode Pulse](http://geode.apache.org/docs/guide/tools_modules/pulse/chapter_overview.html).

`jmx-to-grafana` feeds Geode MBeans metrics data into time-series databases (such as InfluxDB). Later is used in turn to
feed the [Grafana](http://grafana.org/) dashboards. Grafana allows to build comprehensive dashboards.  

The [Geode JMX Grafana Dashboard Video](https://www.youtube.com/watch?v=e2UlWm1w2yY) illustrates the approach. It shows how to deploy and start the `jmx-to-grafana`
and how to build Grafana dashboards using the geode jmx feed.


## Build
Get the source code from github
```
git clone https://github.com/tzolov/geode-dashboard.git
```

From within the geode-dashboard/jmx-to-grafana directory run
```
mvn clean install
```

## Quick Start
Build Grafana dashboard to plot real-time metrics of Geode cluster.
[Grafana](http://docs.grafana.org/installation) and
[InfluxDB](https://docs.influxdata.com/influxdb/v1.1/introduction/installation) have to be installed first.

#### Start JMX To InfluxDB loader
The `jmx-to-grafana` reads every minute (`cronExpression="0 0/1 * * * ?"`) the JMX metrics from
Geode MBean Server (`http://localhost:1099`) and loads them into InfluxDB database (`GeodeJmx`). The InfluxDB is
running at `http://localhost:8086`.

The Grafana server (`http://localhost:3000`) uses the `GeodeJmx` time-series database to plot various `Real-Time` dashboards

```
java -jar ./target/jmx-to-grafana-0.0.1-SNAPSHOT.jar \
   --mbeanHostName=localhost \
   --mbeanPort=1099 \
   --influxUrl=http://localhost:8086 \
   --influxDatabaseName=GeodeJmx \
   --cronExpression="0 0/1 * * * ?"
```

###### Configuration parameters

| Property Name | Default Value | Description |
| ------------- | ------------- | ------------ |
| influxUrl | http://localhost:8086 | InfulxDB connection URL |
| influxUser | admin | InfuxDB connection username |
| influxPassword | admin | InfluxDB connection password |
| cleanDatabaseOnLoad | false | If set the target TSDB will be (re)created on every statistics load |
| influxRetentionPolicy | autogen | InfluxDB retention policy |
| mbeanHostName | None |  |
| influxDatabaseName | GeodeJmx | Database to load the jmx metrics into. Same database is used to load metrics for cluster members. The `member` is used to distinct the time-series form different members. |
| mbeanPort | 1099 |  |
| cronExpression | 0 0/1 * * * ? | Time interval for pulling JMX metrics from Geode and load them into InfluxDB. Defaults to 1m. Use `--cronExpression="..."` syntax to set the expression from the command line. |

###### Exported Geode MBeans
| Geode MBean | InfluxDB Measurement | InfluxDB Meta-tags | Description |
| ------------- | ------------- | ------------ | ------------ |
| [DistributedSystemMXBean](http://bit.ly/2ijH8dj) | DistributedSystem | none | System-wide aggregate MBean that provides a high-level view of the entire distributed system including all members (cache servers, peers, locators) and their caches. At any given point of time, it can provide a snapshot of the complete distributed system and its operations. |
| [DistributedRegionMXBean](http://bit.ly/2ijEnIX) | DistributedRegion | region | System-wide aggregate MBean of a named region. It provides a high-level view of a region for all members hosting and/or using that region. For example, you can obtain a list of all members that are hosting the region. Some methods are only available for partitioned regions. |
| [MemberMXBean](http://bit.ly/2jbLovt) | Member | member | Member’s local view of its connection and cache. It is the primary gateway to manage a particular member. It exposes member level attributes and statistics. |
Consult the [Geode JMX MBeans](http://geode.apache.org/docs/guide/managing/management/list_of_mbeans.html) for the full list of available Geode MBeans.

> *Note*: Use [Grafana Templating](http://docs.grafana.org/reference/templating/) to define query variables for
> the `member` adn `region` meta-tags (e.g. `show tag values with key="member"` or `show tag values with key="region"`).
> This allow mix and match measurments from different cluster members or multiple regions.  

###### Automatic feed re-configuration
The `jmx-to-grafana` reconfigures the feeds automatically on following Geode events:
* New Member is added or removed form the cluster: `gemfire.distributedsystem.cache.member.departed`,`gemfire.distributedsystem.cache.member.joined`.
* New Regions is created for removed: `gemfire.distributedsystem.cache.region.created`, `gemfire.distributedsystem.cache.region.closed`


#### Build Grafana Geode JMX Dashboard
| | |
| ------------- | ------------ |
| ![GeodeJmx Sourxe Definition](../doc/DefineGeodeJmxSource.png) | Define datasource:`GeodeJmx` to the `GeodeJmx` Influx database. Set appropriate InfluxDB URL and credentials. |
| ![Geode HeapUsage Gauge Metrics](../doc/GeodeHeapUsageGaugeMetrics.png) | Create Geode Heap Usage Gauge. Create `Singlestat` panel and select `GeodeJmx` as datasource. Define query: `SELECT "UsedHeapSize" FROM "autogen"."Distributed" WHERE $timeFilter`. |
| ![Geode HeapUsage Gauge Options](../doc/GeodeHeapUsageGaugeOptions.png) | Within the `Options` tab set the value stat to `current`, check the `Spark Line Show` and `Gauge Show` boxes. |
| ![Geode HeapUsage Gauge](../doc/GeodeHeapUsageGauge.png) | Result gauge would look like this |

Explore the `Predefined Dashboards` for more comprehensive dashboards panels.

#### Predefined Dashboards
Use or customize the sample [Geode Grafana Dashboards](./src/main/resources/dashboards) to visualize Cluster, Members or Regions view of the distributed system.
