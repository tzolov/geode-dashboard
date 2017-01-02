# Apache Geode real-time JMX Metrics Plot with Grafana

[Apache Geode](http://geode.apache.org/) uses a federated [Open MBean](http://docs.oracle.com/cd/E19206-01/816-4178/6madjde4v/index.html) strategy to manage and monitor all members of the distributed system. Your Java classes interact with a single MBeanServer that aggregates MBeans from other local and remote members. Using this strategy gives you a consolidated, single-agent view of the distributed system.					
One can use generic JMX clients to monitor or manage the Geode distributed system by using any third-party tool that compliant with JMX such as JConsole and Geode Pulse
Geode Pulse is a Web Application that provides a graphical dashboard for monitoring vital, real-time health and performance of GemFire clusters, members, and regions.
Internally Pulse communicates with a GemFire JMX manager to provide a complete view of your GemFire deployment. 


`jmx-to-grafana` continuously load the Geode cluster MBeans metrics into time-series databases (such as InfluxDB), in turn used to feed [Gfafana](http://grafana.org/) dashboards. Grafana allows to build comprehensive dashboards.  

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
[Grafana](http://docs.grafana.org/installation) and [InfluxDB](https://docs.influxdata.com/influxdb/v1.1/introduction/installation) have to be installed first. Samples below expect InfluxDB on `http://localhost:8086` and Grafana on `http://localhost:30000`. 

#### Start Jmx To Grafana daemon

```
java -jar ./target/jmx-to-grafana-0.0.1-SNAPSHOT.jar 
   --influxUrl=http://localhost:8086 
   --influxDatabaseName=GeodeJmx 
   --mbeanHostName=localhost 
   --mbeanPort=1199
```

Complete list of statistics-to-grafana parameters:

| Property Name | Default Value | Description |
| ------------- | ------------- | ------------ |
| influxUrl | http://localhost:8086 | InfulxDB connection URL |
| influxUser | admin | InfuxDB connection username |
| influxPassword | admin | InfluxDB connection password |
| cleanDatabaseOnLoad | false | If set the target TSDB will be (re)created on every statistics load |
| influxRetentionPolicy | autogen | InfluxDB retention policy |
| mbeanHostName | None |  |
| influxDatabaseName | GeodeJmx | Database to load the jmx metrics into. Same database is used to load metrics for cluster members. The `member` is used to distinct the time-series form different members. |
| mbeanPort | 1190 |  |
| cronExpression | 0 0/1 * * * ? | Time interval for pulling JMX metrics from Geode and load them into InfluxDB. Defaults to 1m. Use `--cronExpression="..."` syntax to set the expression from the command line. |

#### Build Grafana Goede JMX Dashboard
* Define datasource:`GeodeJmx` to the `GeodeJmx` Influx database. Set appropriate InfluxDB URL and credentials.
* ... TODO
