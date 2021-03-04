package com.atguigu.offline

import org.apache.spark.SparkConf
import org.apache.spark.mllib.recommendation.{ALS, Rating}
import org.apache.spark.sql.SparkSession

/**
  * Movie数据集，数据集字段通过分割
  *
  * 151^                          电影的ID
  * Rob Roy (1995)^               电影的名称
  * In the highlands ....^        电影的描述
  * 139 minutes^                  电影的时长
  * August 26, 1997^              电影的发行日期
  * 1995^                         电影的拍摄日期
  * English ^                     电影的语言
  * Action|Drama|Romance|War ^    电影的类型
  * Liam Neeson|Jessica Lange...  电影的演员
  * Michael Caton-Jones           电影的导演
  *
  * tag1|tag2|tag3|....           电影的Tag
  **/

case class Movie(val mid: Int, val name: String, val descri: String, val timelong: String, val issue: String,
                 val shoot: String, val language: String, val genres: String, val actors: String, val directors: String)

/**
  * Rating数据集，用户对于电影的评分数据集，用，分割
  *
  * 1,           用户的ID
  * 31,          电影的ID
  * 2.5,         用户对于电影的评分
  * 1260759144   用户对于电影评分的时间
  */
case class MovieRating(val uid: Int, val mid: Int, val score: Double, val timestamp: Int)

/**
  * MongoDB的连接配置
  * @param uri   MongoDB的连接
  * @param db    MongoDB要操作数据库
  */
case class MongoConfig(val uri:String, val db:String)

//推荐
case class Recommendation(rid:Int, r:Double)

// 定义基于预测评分的用户推荐列表
case class UserRecs(uid:Int, recs:Seq[Recommendation])

//定义基于电影的相似度列表
case class MovieRecs(uid:Int, recs:Seq[Recommendation])

object OfflineRecommender {

  //定义表名
  val MONGODB_RATING_COLLECTION = "Rating"
  val MONGODB_MOVIE_COLLECTION = "Movie"

  val USER_MAX_RECOMMENDATION = 20

  val USER_RECS = "UserRecs"

  //入口方法
  def main(args: Array[String]): Unit = {


    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> "mongodb://linux:27017/recommender",
      "mongo.db" -> "reommender"
    )

    //创建一个SparkConf配置
    val sparkConf = new SparkConf().setAppName("OfflineRecommender").setMaster(config("spark.cores"))
      .set("spark.executor.memory","6G").set("spark.driver.memory","3G")

    //基于SparkConf创建一个SparkSession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    //创建一个MongoDBConfig
    val mongoConfig = MongoConfig(config("mongo.uri"),config("mongo.db"))

    import spark.implicits._

    //读取mongoDB中的业务数据
    val ratingRDD = spark
      .read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[MovieRating]
      .rdd   //转成rdd 因为后面调用als算法的参数是rdd类型
      .map(rating=> (rating.uid, rating.mid, rating.score))

    //用户的数据集 RDD[Int] 从rating数据中提取所有的mid并且去重
    val userRDD = ratingRDD.map(_._1).distinct()

    //电影数据集 RDD[Int]  只要mid
    val movieRDD = spark
      .read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_MOVIE_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Movie]
      .rdd
      .map(_.mid)

    //创建训练数据集  将ratingRDD(rating.uid, rating.mid, rating.score)转换成Mlib中Rating
    val trainData = ratingRDD.map(x => Rating(x._1,x._2,x._3)) //

    val (rank,iterations,lambda) = (50, 5, 0.01)
    //训练ALS模型
    val model = ALS.train(trainData,rank,iterations,lambda)

    //计算用户推荐矩阵

      //需要构造一个usersProducts  RDD[(Int,Int)]
      // 得到一个空评分矩阵，有userRDD和movieRdd做笛卡尔积得到
    val userMovies = userRDD.cartesian(movieRDD)

    //调用predict方法得到预测评分
    val preRatings = model.predict(userMovies)

    //基于preRatings的推荐列表
    val userRecs = preRatings
      .filter(_.rating > 0)   //过滤出评分>0的项
      .map(rating => (rating.user,(rating.product, rating.rating)))  //得到一个uid 后面一个元组类型的数据格式
      .groupByKey()
      .map{
        //sortWith(_._2 > _._2) 根据第二个元素降序排列 因为resc中都是(rating.product, rating.rating)的元祖，中国第二个项(分数)来拍
        case (uid,recs) => UserRecs(uid,recs.toList.sortWith(_._2 > _._2).take(USER_MAX_RECOMMENDATION).map(x => Recommendation(x._1,x._2)))
      }.toDF()

    //写入mongo中
    userRecs.write
      .option("uri",mongoConfig.uri)
      .option("collection",USER_RECS)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //计算电影相似度矩阵


    //关闭Spark
    spark.close()
  }

}
