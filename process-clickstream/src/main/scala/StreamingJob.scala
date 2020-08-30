
import org.apache.spark.sql.{DataFrame, ForeachWriter, SaveMode, SparkSession}
import org.apache.spark.sql.functions.{col, from_json, split}
import org.apache.spark.sql.types.{StructField, StructType, _}
import config.Settings
import com.datastax.spark.connector._
import org.apache.spark.SparkConf

object StreamingJob {
  // 1. Save to Stream to dblambda.stream_click_events
  def main(args: Array[String]): Unit = {
    System.setProperty("hadoop.home.dir", "/")
    val sparkSession = SparkSession
      .builder()
      .master("local[*]")
      .appName("Click Data Stream")
      .config("spark.cassandra.connection.host", "ec2-54-77-240-13.eu-west-1.compute.amazonaws.com")
      .config("spark.cassandra.connection.port", "9042")
      //set spark.cassandra.auth.username & spark.cassandra.auth.password (if authentication is enabled)
      .getOrCreate()
    sparkSession.sparkContext.setLogLevel("ERROR")
    val wlc = Settings.WebLogGen

    val inputDf = sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "weblogs-text")
      .option("startingOffsets", "latest")
      .load()

    import sparkSession.implicits._

     val clickEventSchema= StructType(Seq(
       StructField("eventTime",LongType,true),
       StructField("referrer",StringType,true),
       StructField("action",StringType,true),
       StructField("prevPage",StringType,true),
       StructField("visitor",StringType,true),
       StructField("page",StringType,true),
       StructField("product",StringType,true))
     )

    // >>>>>>>>> If the stream data is <<<<
    //       .selectExpr("CAST(value AS STRING) as string_value")
    //       .as[String]
    //       .map(x => (x.split(";")))
    //       .map(x => tweet(x(0), x(1), x(2),  x(3), x(4), x(5)))
    //       .selectExpr( "cast(id as long) id", "CAST(created_at as timestamp) created_at",  "cast(followers_count as int) followers_count", "location", "cast(favorite_count as int) favorite_count", "cast(retweet_count as int) retweet_count")
    //       .toDF()


    val MS_IN_HOUR = 1000 * 60 * 60
    val activityStream = inputDf
      .selectExpr("CAST(timestamp AS STRING)", "CAST(value AS STRING)")
      .withColumn("json",
        from_json($"value".cast(StringType), clickEventSchema)
      )
     .withColumn("timestamp_hour",col("json.eventTime")/ MS_IN_HOUR * MS_IN_HOUR)
      .select(
        col("json.eventTime").alias("eventtime"),
        col("json.referrer"),
        col("json.action"),
        col("json.prevPage").alias("prevpage"),
        col("json.visitor"),
        col("json.page"),
        col("json.product"),
        col("timestamp_hour")
      )

    activityStream.createOrReplaceTempView("activity")
    val activityByProduct = sparkSession.sql("""SELECT
                                            product,
                                            timestamp_hour,
                                            sum(case when action = 'purchase' then 1 else 0 end) as purchase_count,
                                            sum(case when action = 'add_to_cart' then 1 else 0 end) as add_to_cart_count,
                                            sum(case when action = 'page_view' then 1 else 0 end) as page_view_count
                                            from activity
                                            group by product, timestamp_hour """)
    import org.apache.spark.sql.cassandra._

    activityByProduct.writeStream
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>

        batchDF.write       // Use Cassandra batch data source to write streaming out
          .cassandraFormat("stream_activity_by_product", "dblambda")
          .option("cluster", "Test Cluster")
          .mode("append")
          .save()
      }
      .outputMode("update")
      .start()
      .awaitTermination()
    // https://github.com/dorianbg/lambda-architecture-demo

    /*  >>>> Append output mode not supported when there are streaming aggregations on streaming DataFrames/DataSets without watermark;;

          .writeStream
          .outputMode("complete")
          //.option("checkpointLocation", "s3://checkpointjoin_delta")
          .format("console")
          .start()
        consoleOutput.awaitTermination()
    */


    //    val query = data_stream_cleaned.writeStream
    //      .format("memory")
    //      .queryName("demo")
    //      .trigger(ProcessingTime("60 seconds"))   // means that that spark will look for new data only every minute
    //      .outputMode("complete") // could also be complete or update
    //      .start()

  }
}
