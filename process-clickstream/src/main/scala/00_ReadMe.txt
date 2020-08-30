There are a few types of built-in output sinks.

File sink - Stores the output to a directory.
------------------------------------------
writeStream
    .format("parquet")        // can be "orc", "json", "csv", etc.
    .option("path", "path/to/destination/dir")
    .start()

Kafka sink - Stores the output to one or more topics in Kafka.
------------------------------------------
writeStream
    .format("kafka")
    .option("kafka.bootstrap.servers", "host1:port1,host2:port2")
    .option("topic", "updates")
    .start()

Foreach/foreachBatch sink - Runs arbitrary computation on the records in the output. See later in the section for more details.
------------------------------------------
The foreach and foreachBatch operations allow you to apply arbitrary operations and writing logic on the output of a streaming query. They have slightly different use cases - while foreach allows custom write logic on every row, foreachBatch allows arbitrary operations and custom logic on the output of each micro-batch
writeStream
    .foreach(...)
    .start()

streamingDF
            .writeStream
            .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
                 // Transform and write batchDF
                           }.start()

Console sink (for debugging) - Prints the output to the console/stdout every time there is a trigger. Both, Append and Complete output modes, are supported. This should be used for debugging purposes on low data volumes as the entire output is collected and stored in the driver’s memory after every trigger.
------------------------------------------
writeStream
    .format("console")
    .start()

Memory sink (for debugging) - The output is stored in memory as an in-memory table. Both, Append and Complete output modes, are supported. This should be used for debugging purposes on low data volumes as the entire output is collected and stored in the driver’s memory. Hence, use it with caution.
------------------------------------------
writeStream
    .format("memory")
    .queryName("tableName")
    .start()

####################################################################################################################
For Spark 2.4.0 and higher, you can use the foreachBatch method, which allows you to use the Cassandra batch data writer provided by the Spark Cassandra Connector to write the output of every micro-batch of the streaming query to Cassandra:

import org.apache.spark.sql.cassandra._

df.writeStream
  .foreachBatch { (batchDF, _) =>
    batchDF
     .write
     .cassandraFormat("tableName", "keyspace")
     .mode("append")
     .save
  }.start

For Spark versions lower than 2.4.0, you need to implement a foreach sink.
    import com.datastax.spark.connector.cql.CassandraConnector
    import com.datastax.spark.connector._


    class SparkSessionBuilder extends Serializable {
      // Build a spark session. Class is made serializable so to get access to SparkSession in a driver and executors.
      // Note here the usage of @transient lazy val
      def buildSparkSession: SparkSession = {
        @transient lazy val conf: SparkConf = new SparkConf()
          .setAppName("Structured Streaming from Kafka to Cassandra")
          .set("spark.cassandra.connection.host", "ec2-52-23-103-178.compute-1.amazonaws.com")
          .set("spark.sql.streaming.checkpointLocation", "checkpoint")
        @transient lazy val spark = SparkSession
          .builder()
          .config(conf)
          .getOrCreate()
        spark
      }
    }


    class CassandraDriver extends SparkSessionBuilder {
      // This object will be used in CassandraSinkForeach to connect to Cassandra DB from an executor.
      // It extends SparkSessionBuilder so to use the same SparkSession on each node.
      val spark = buildSparkSession
      import spark.implicits._
      val connector = CassandraConnector(spark.sparkContext.getConf)
      // Define Cassandra's table which will be used as a sink
      /* For this app I used the following table:
           CREATE TABLE fx.spark_struct_stream_sink (
           fx_marker text,
           timestamp_ms timestamp,
           timestamp_dt date,
           primary key (fx_marker));
      */
      val namespace = "fx"
      val foreachTableSink = "spark_struct_stream_sink"
    }

    class CassandraSinkForeach() extends ForeachWriter[org.apache.spark.sql.Row] {
      // This class implements the interface ForeachWriter, which has methods that get called
      // whenever there is a sequence of rows generated as output
      val cassandraDriver = new CassandraDriver();
      def open(partitionId: Long, version: Long): Boolean = {
        // open connection
        println(s"Open connection")
        true
      }
      def process(record: org.apache.spark.sql.Row) = {
        println(s"Process new $record")
        cassandraDriver.connector.withSessionDo(session =>
          session.execute(s"""
       insert into ${cassandraDriver.namespace}.${cassandraDriver.foreachTableSink} (fx_marker, timestamp_ms, timestamp_dt)
       values('${record(0)}', '${record(1)}', '${record(2)}')""")
        )
      }
      def close(errorOrNull: Throwable): Unit = {
        // close the connection
        println(s"Close connection")
      }
    }
  // Output results into a database
  val sink = consoleOutput
    .writeStream
    .queryName("KafkaToCassandraForeach")
    .outputMode("update")
    .foreach(new CassandraSinkForeach())
    .start()
    sink.awaitTermination()