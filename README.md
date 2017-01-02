# Apache Geode - Grafana Dashboard

Apache Geode tools for analysing and visualising historical and real-time statistics with Grafana.

![Apache Geode Grafana Dashboards](./doc/geode-dashboards.png)

Following projects allow real-time ([jmx-to-grafana](./jmx-to-grafana)) 
and historical ([statistics-to-grafana](./statistics-to-grafana)) metrics monitoring.

## [JMX-To-Grafana](https://github.com/tzolov/geode-dashboard/tree/master/jmx-to-grafana) 
Geode distributed system real-time metrics visualization with Grafana dashboard. 
Geode uses a federated `Open MBean`  to manage and monitor all members of the distributed system. Single MBeanServer aggregates 
MBeans from local and remote members and provides a consolidated, single-agent view of the 
distributed system.	`jmx-to-grafana` is a generic, JMX compliant client that feeds the JMX metrics
to InfluxDB database. Grafana consumes the feeds and provides graphical dashboards for monitoring vital, real-time 
health and performance of Geode clusters, members, and regions.
Internally `jmx-to-grafana` communicates with a Geode JMX manager to provide a complete view of 
your Geode deployment. 

## [Statistics-To-Gafana](https://github.com/tzolov/geode-dashboard/tree/master/statistics-to-grafana) 
Leverage Grafana (metric & analytic dashboards tool) for querying, visualizing and analysing [Apache Geode & Gemfire Statistics Archives](http://geode.apache.org/docs/guide/managing/statistics/chapter_overview.html). 
Geode can collect statistics about the distributed system and persist it in archive files. The `statistics-to-grafana` 
tool loads later into a Grafana supported time-series database such as InfluxDB. Then one can 
build comprehensive Grafana dashboards to visualize and analyse the statistics data.

## [Geode Grafana dashboards](./jmx-to-grafana/src/main/resources/dashboards)
Sample Grafana dashboards providing `Cluster`, `Members` and `Regions` views on the Geode 
distributed system. Use and extend them to build your own dashboards. 

## Architecture Overview
![Apache Geode Grafana Dashboards Architecture](./doc/GeodeDashboardArchitecture.png)
* Real-Time Metrics Monitoring - Start the Gedoe Cluster with JMX RMI enabled port. ([jmx-to-grafana](./jmx-to-grafana)) is standalone spring-boot 
application that reads (on pre-defined intervals) the current Geode metrics from the Geode MBean Server.
    
