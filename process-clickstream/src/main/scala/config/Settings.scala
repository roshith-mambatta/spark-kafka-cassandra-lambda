package config

import com.typesafe.config.ConfigFactory

/**
  * Created by Ahmad Alkilani on 4/30/2016.
 * https://gist.github.com/diegopacheco/2c36ba8bca1c3a2e44c5f422357a060e#file-cassandra-3-x-cluster-ec2-md
  */
object Settings {
  private val config = ConfigFactory.load()

  object WebLogGen {
    private val weblogGen = config.getConfig("clickstream")

    lazy val records = weblogGen.getInt("records")
    lazy val timeMultiplier = weblogGen.getInt("time_multiplier")
    lazy val pages = weblogGen.getInt("pages")
    lazy val visitors = weblogGen.getInt("visitors")
    lazy val filePath = weblogGen.getString("file_path")
    lazy val destPath = weblogGen.getString("dest_path")
    lazy val numberOfFiles = weblogGen.getInt("number_of_files")
    lazy val kafkaTopic = weblogGen.getString("kafka_topic")
    lazy val hdfsPath = weblogGen.getString("hdfs_path")
  }
}
