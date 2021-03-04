package com.atguigu.statisticsRecommender

import java.text.SimpleDateFormat
import java.util.Date

import com.atguigu.statisticsRecommender.StatisticsApp.{RATINGS_COLLECTION_NAME, mongoConf, spark}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Dataset, Row, SparkSession}


// MongoDB Config
case class MongoConfig(val uri: String, val db: String)

// Rating Class
case class Rating(mid: Int, uid: Int, score: Double, timestamp: Long)

//定义一个基准推荐对象   （mid，score）
/*
rid 推荐的Movie的mid
r   Movie的评分
 */
case class Recommendation(rid: Int, r: Double)

//定义电影类别top10推荐对象
/*
genres 电影类别
recs   top10的电影的集合
 */
case class GenresRecommendation(genres: String, recs: Seq[Recommendation])


/**
  * Movie Class 电影类
  *
  * @param mid       电影的ID
  * @param name      电影的名称
  * @param descri    电影的描述
  * @param timelong  电影的时长
  * @param issue     电影的发行时间
  * @param shoot     电影的拍摄时间
  * @param language  电影的语言
  * @param genres    电影的类别
  * @param actors    电影的演员
  * @param directors 电影的导演
  */
case class Movie(val mid: Int, val name: String, val descri: String, val timelong: String, val issue: String, val shoot: String, val language: String, val genres: String, val actors: String, val directors: String)


object statisticsRecommender {

  val RATE_MORE_MOVIES = "RateMoreMovies"
  val RATE_MORE_MOVIES_RECENTLY = "RateMoreMoviesRecently"
  val AVERAGE_MOVIES_SCORE = "AverageMoviesScore"
  val GENRES_TOP_MOVIES = "GenresTopMovies"


  /**
    * 评分最多统计 历史评分数据最多
    */
  def rateMore(spark: SparkSession)(implicit mongoConf: MongoConfig): Unit = {

    //数据结构 => mid,count
    val rateMoreDF = spark.sql("select mid, count(mid) as count from ratings group by mid order by count desc")

    //写入mongo表中
    rateMoreDF
      .write
      .option("uri", mongoConf.uri)
      .option("collection", RATE_MORE_MOVIES)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()
  }

  /**
    * 近期热门电影统计 按照"yyyyMM"格式选取最近的评分数据，统计评分个数
    */
  def rateMoreRecently(spark: SparkSession)(implicit mongoConf: MongoConfig): Unit = {

    //创建一个日期格式化的变量
    val simpleDateFormat = new SimpleDateFormat("yyyyMM")
    //注册udf方法  参数x
    spark.udf.register("changDate", (x: Long) => simpleDateFormat.format(new Date(x * 1000L)).toLong)

    //将timestamp 转换成年月的格式
    val yeahMonthOfRatings = spark.sql("select mid, uid, score, changDate(timestamp) as yeahmonth from ratings")

    //将新的数据集注册成一张表
    yeahMonthOfRatings.createOrReplaceTempView("ymRatings")

    //对mid做统计
    // //数据结构 => mid,count,time
    val rateMoreRecentlyDF = spark.sql("select mid, count(mid) as count,yeahmonth from ymRatings group by yeahmonth,mid order by yeahmonth desc,count desc")

    //存入mongodb中
    rateMoreRecentlyDF
      .write
      .option("uri", mongoConf.uri)
      .option("collection", RATE_MORE_MOVIES_RECENTLY)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()
  }

  /**
    * top10 统计电影的平均评分
    */
  def averageMovieScore(spark: SparkSession)(movies: Dataset[Movie])(implicit mongoConf: MongoConfig): Unit = {

    val genres = List("Action","Adventure","Animation","Comedy","Ccrime","Documentary","Drama","Family","Fantasy","Foreign","History","Horror","Music","Mystery"
    ,"Romance","Science","Tv","Thriller","War","Western")

    //统计每个电影的平均评分
    val averageMovieScoreDF = spark.sql("select mid, avg(score) as avg from ratings group by mid").cache()

    // 统计每种类别最热电影【每种类别中平均评分最高的10部电影】
    val moviesWithSocreDF = movies.join(averageMovieScoreDF,Seq("mid","mid")).select("mid","avg","genres").cache()

    //为做笛卡尔积，把genres转成rdd
    val genresRdd = spark.sparkContext.makeRDD(genres);

    import spark.implicits._

    //笛卡尔积操作  且过滤
    val genresTopMovies = genresRdd.cartesian(moviesWithSocreDF.rdd).filter{
      case (genres,row) => {
        //找出movies的字段有genres值包含当前类别的那些
        row.getAs[String]("genres").toLowerCase().contains(genres.toLowerCase)
      }
    }.map{
      case (genres,row) => {
        (genres,(row.getAs[Int]("mid"),row.getAs[Double]("avg")))   //seq
      }
    }.groupByKey()
        .map{
          case (genres,items) => {
            GenresRecommendation(genres,items.toList.sortWith(_._2 > _._2).slice(0,10).map(x=>Recommendation(x._1,x._2)))
          }
        }.toDF

    genresTopMovies
      .write
      .option("uri", mongoConf.uri)
      .option("collection", GENRES_TOP_MOVIES)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    averageMovieScoreDF
      .write
      .option("uri", mongoConf.uri)
      .option("collection", AVERAGE_MOVIES_SCORE)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()
  }

  /**
    * 每种类别最热电影推荐
    */
  def genresHot(spark: SparkSession)(implicit mongoConf: MongoConfig): Unit = {

    val simpleDateFormat = new SimpleDateFormat("yyyyMM")
    spark.udf.register("changDate", (x: Long) => simpleDateFormat.format(new Date(x * 1000L)).toLong)

    val yeahMonthOfRatings = spark.sql("select mid, uid, score, changDate(timestamp) as yeahmonth from ratings")

    yeahMonthOfRatings.createOrReplaceTempView("ymRatings")

//    val rateMoreRecentlyDF = spark.sql("select mid, count(mid) as count,yeahmonth from ymRatings group by yeahmonth,mid order by yeahmonth desc,count desc")


    val rateMoreRecentlyDF = spark.sql("select mid, avg(score) as count,yeahmonth from ymRatings group by yeahmonth,mid order by yeahmonth desc,count desc")
    rateMoreRecentlyDF
      .write
      .option("uri", mongoConf.uri)
      .option("collection", RATE_MORE_MOVIES_RECENTLY)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()
  }

}


