import config.Settings
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col,from_json,dayofmonth,current_date,month,year}
import org.apache.spark.sql.types.{LongType, StringType, StructField, StructType}

object WriteFromKafkaToS3 {
  def main(args: Array[String]): Unit = {
    System.setProperty("hadoop.home.dir", "/")
    val sparkSession = SparkSession
      .builder()
      .master("local[*]")
      .appName("Click Data Stream")
      .config("spark.cassandra.connection.host", "ec2-54-77-240-13.eu-west-1.compute.amazonaws.com")
      .config("spark.cassandra.connection.port", "9042")
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

    // This is the schema of incoming data from Kafka:

    // StructType(
    // StructField(key,BinaryType,true),
    // StructField(value,BinaryType,true),
    // StructField(topic,StringType,true),
    // StructField(partition,IntegerType,true),
    // StructField(offset,LongType,true),
    // StructField(timestamp,TimestampType,true),
    // StructField(timestampType,IntegerType,true)
    // )
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

    val MS_IN_HOUR = 1000 * 60 * 60
    val activityStream = inputDf
      .selectExpr("CAST(timestamp AS STRING)", "CAST(value AS STRING)")
      .withColumn("json", // nested structure with our json
        from_json($"value".cast(StringType), clickEventSchema) //From binary to JSON object
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
        col("timestamp_hour"),
        dayofmonth(current_date()).alias("day"),
        month(current_date()).alias("month"),
        year(current_date()).alias("year")
        )

    // store this data in S3 based on the day it's received:: s3_bucket/year/month/day/
    activityStream
      .writeStream
          .partitionBy("year", "month", "day")
          .outputMode("append")
          .format("parquet")
          .queryName("clickStream")
          .option("checkpointLocation", "C:\\00_MyDrive\\ApacheSpark\\00_Projects\\exercise-4\\weblogs-chkpoint_dir")
          .start(wlc.hdfsPath)
          .awaitTermination()

  }
}
