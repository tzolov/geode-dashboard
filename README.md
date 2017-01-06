# Apache Geode - Grafana Dashboard

Apache Geode tools for analysing and visualising historical and real-time statistics with Grafana.

![Apache Geode Grafana Dashboards](./doc/geode-dashboards.png)

Following projects allow real-time ([jmx-to-grafana](./jmx-to-grafana)) 
and historical ([statistics-to-grafana](./statistics-to-grafana)) metrics monitoring.

## Architecture
`Geode JMX Metrics Analysis` - Geode implements federated `JMX` architecture to manage and monitor all members of the distributed system. 
The [jmx-to-grafana](./jmx-to-grafana) spring-boot application consumes selected Geode MBean metrics and streams them 
into a InfluxDB time-series database. Grafana uses the time-series to build comprehensive dashboards to monitor and analyze the distributed system.

`Geode Statistics Analysis` -  Geode collects [detailed statistics](http://geode.apache.org/docs/guide/managing/statistics/chapter_overview.html) 
about the distributed system. Statistics is persisted in archive files. You can use [statistics-to-grafana](./statistics-to-grafana) 
tool load and converts the archive files into InfluxDB time-series database. Use Grafana to build comprehensive Grafana 
dashboards to visualize and  analyse the statistics data. 
 
![Apache Geode Grafana Dashboards Architecture](./doc/GeodeDashboardArchitecture.png)

## [Geode JMX To Grafana](./jmx-to-grafana) 
[<img align="left" src="http://img.youtube.com/vi/e2UlWm1w2yY/0.jpg" alt="zeppelin-view" hspace="10" width="130"></img>](https://www.youtube.com/watch?v=e2UlWm1w2yY)
Geode distributed system real-time metrics visualization with Grafana dashboard. 
Geode uses a federated `Open MBean`  to manage and monitor all members of the distributed system. Single MBeanServer aggregates 
MBeans from local and remote members and provides a consolidated, single-agent view of the 
distributed system.	`jmx-to-grafana` is a generic, JMX compliant client that feeds the JMX metrics
to InfluxDB database. Grafana consumes the feeds and provides graphical dashboards for monitoring vital, real-time 
health and performance of Geode clusters, members, and regions.
Internally `jmx-to-grafana` communicates with a Geode JMX manager to provide a complete view of 
your Geode deployment. 
The [Geode JMX Grafana Video](https://www.youtube.com/watch?v=e2UlWm1w2yY) illustrates the approach. It shows how to deploy and start the `jmx-to-grafana` 
and how to build Grafana dashboards using the geode jmx feed.

## [Geode Statistics To Grafana](./statistics-to-grafana) 
Leverage Grafana (metric & analytic dashboards tool) for querying, visualizing and analysing [Apache Geode & Gemfire Statistics Archives](http://geode.apache.org/docs/guide/managing/statistics/chapter_overview.html). 
Geode can collect statistics about the distributed system and persist it in archive files. The `statistics-to-grafana` 
tool loads later into a Grafana supported time-series database such as InfluxDB. Then one can 
build comprehensive Grafana dashboards to visualize and analyse the statistics data.

## [Geode Grafana Dashboards](./jmx-to-grafana/src/main/resources/dashboards)
Sample Grafana dashboards providing `Cluster`, `Members` and `Regions` views on the Geode 
distributed system. Use and extend them to build your own dashboards. 
