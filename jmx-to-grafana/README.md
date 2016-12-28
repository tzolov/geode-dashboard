# Apache Geode real-time JMX Metrics Plot with Grafana

[Apache Geode](http://geode.apache.org/) can collect [Statistics](http://geode.apache.org/docs/guide/managing/statistics/chapter_overview.html) about the distributed system and persist it in archive files. The `StatisticsToGrafana` tool helps to load the archive files in time-series databases such as InfluxDB used to feed [Gfafana](http://grafana.org/) dashboards. 
Use the stack to build comprehensive [Gfafana](http://grafana.org/) dashboards to visualize, analyse and compare statistics data from different cluster members or even different Geode clusters.

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

#### Build Grafana Goede JMX Dashboard
* Define datasource:`GeodeJmx` to the `GeodeJmx` Influx database. Set approriate InfluxDB URL and credentials. 
![Member Template Definition](../doc/geode_grafana_statistic_archive_datasource.png)
* Create new Grafana dashboard named `ArchiveDashboard`
* Inside `ArchiveDashboard` create member tag Template (`show tag values with key="archiveMember"`). 
Set `GeodeArchive` as Data Source and enable the `Multi-value` option. Later allows to select the members to show statistics for.  
![Member Template Definition](../doc/geode_grafana_member_template_definition.png)
* Create Number-Of-Threads-Per-Member panel to plot the number of threads used by each Geode member.
![Number of Threads Per Member Definition](../doc/geode_grafana_panel_definition.png)
Pick `GeodeArchive` as panel's data source. Set the retention policy to `autogen`. In the `FROM` dropdown clause pick the `VMStats:vmStats` measurement. 
Use the `member` template variable in the `WHERE` and `GROUP BY` clauses to (sub)select the members to visualize.`
* Adjust the time range to reflect the interval when the statistic is collected
![Number of Threads Per Member Panel](../doc/geode_grafana_thread_number_diagram.png)



