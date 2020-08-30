import config.Settings

object BatchJob {

  // Read from HDFS OR S3
  def main(args: Array[String]): Unit = {
    import org.apache.spark.sql.{SaveMode, SparkSession}
    val sparkSession = SparkSession
      .builder()
      .master("local[*]")
      .appName("Click Data Stream")
      .config("spark.cassandra.connection.host", "ec2-54-77-240-13.eu-west-1.compute.amazonaws.com")
      .config("spark.cassandra.connection.port", "9042")
      .getOrCreate()
    sparkSession.sparkContext.setLogLevel("ERROR")

    val wlc = Settings.WebLogGen

    val inputDF = sparkSession.read.parquet(wlc.hdfsPath)
         // .where("unix_timestamp() - timestamp_hour / 1000 <= 60 * 60 * 6")

    inputDF.createOrReplaceTempView("activity")

    val visitorsByProduct = sparkSession.sql(
      """SELECT product, timestamp_hour, COUNT(DISTINCT visitor) as unique_visitors
        |FROM activity GROUP BY product, timestamp_hour
      """.stripMargin)

    visitorsByProduct
      .write
      .format("org.apache.spark.sql.cassandra")
      .options(Map( "keyspace" -> "dblambda", "table" -> "batch_visitors_by_product"))
      .save()

    val activityByProduct = sparkSession.sql("""SELECT
                                            product,
                                            timestamp_hour,
                                            sum(case when action = 'purchase' then 1 else 0 end) as purchase_count,
                                            sum(case when action = 'add_to_cart' then 1 else 0 end) as add_to_cart_count,
                                            sum(case when action = 'page_view' then 1 else 0 end) as page_view_count
                                            from activity
                                            group by product, timestamp_hour """).cache()

    activityByProduct
      .write
      .format("org.apache.spark.sql.cassandra")
      .options(Map( "keyspace" -> "dblambda", "table" -> "batch_activity_by_product"))
      .save()

    }
  }
