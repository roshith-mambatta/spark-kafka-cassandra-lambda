name := "clickstream"

version := "0.1"

scalaVersion := "2.11.8"

//kafka dependencies
libraryDependencies += "org.apache.spark" %% "spark-sql-kafka-0-10" % "2.2.0"

libraryDependencies += "com.typesafe" % "config" % "1.2.1"
libraryDependencies += "net.liftweb" %% "lift-json" % "3.4.1"
