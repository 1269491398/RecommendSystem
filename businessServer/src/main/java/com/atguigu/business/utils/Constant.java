package com.atguigu.business.utils;

public class Constant {

    //************** FOR MONGODB ****************

    public static String MONGODB_DATABASE = "recommender";

    //用户表
    public static String MONGODB_USER_COLLECTION= "User";

    //电影表名
    public static String MONGODB_MOVIE_COLLECTION = "Movie";

    //电影评分表
    public static String MONGODB_RATING_COLLECTION = "Rating";

    //电影标签表
    public static String MONGODB_TAG_COLLECTION = "Tag";

    //电影平均评分表
    public static String MONGODB_AVERAGE_MOVIES_SCORE_COLLECTION = "AverageMovies";

    //电影的相似矩阵
    public static String MONGODB_MOVIE_RECS_COLLECTION = "MovieRecs";

    //优质电影表
    public static String MONGODB_RATE_MORE_MOVIES_COLLECTION = "RateMoreMovies";

    //最热电影表
    public static String MONGODB_RATE_MORE_MOVIES_RECENTLY_COLLECTION = "RateMoreRecentlyMovies";

    //实时推荐表
    public static String MONGODB_STREAM_RECS_COLLECTION = "StreamRecs";

    //用户的推荐矩阵
    public static String MONGODB_USER_RECS_COLLECTION = "UserRecs";

    //电影类别top10表
    public static String MONGODB_GENRES_TOP_MOVIES_COLLECTION = "GenresTopMovies";



    //************** FOR ELEASTICSEARCH ****************

    public static String ES_INDEX = "recommender";

    public static String ES_MOVIE_TYPE = "Movie";


    //************** FOR MOVIE RATING ******************

    //埋点日志头
    public static String MOVIE_RATING_PREFIX = "USER_RATING_LOG_PREFIX:";

    public static int REDIS_MOVIE_RATING_QUEUE_SIZE = 40;
}
