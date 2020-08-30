name := "process-clickstream"

version := "0.1"

scalaVersion := "2.11.8"
val sparkVersion = "2.4.5"

libraryDependencies += "com.typesafe" % "config" % "1.2.1"

// Spark Core, Spark SQL and Spark Streaming dependencies
libraryDependencies += "org.apache.spark" %% "spark-core" % sparkVersion
libraryDependencies += "org.apache.spark" %% "spark-sql" % sparkVersion
libraryDependencies += "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion
libraryDependencies += "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided"

libraryDependencies += "com.datastax.spark" %% "spark-cassandra-connector" % "2.4.3"
