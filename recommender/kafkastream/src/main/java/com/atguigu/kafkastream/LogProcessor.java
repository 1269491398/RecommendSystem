/*
 * Copyright (c) 2017. WuYufei All rights reserved.
 */

package com.atguigu.kafkastream;

import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

/**
 */
public class LogProcessor implements Processor<byte[],byte[]> {
    private ProcessorContext context;

    public void init(ProcessorContext context) {
        this.context = context;
    }

    public void process(byte[] dummy, byte[] line) {
        //把收集到的日志信息用string表示
        String input = new String(line);
        //判断有没有
        if(input.contains("MOVIE_RATING_PREFIX:")){
            input = input.split("MOVIE_RATING_PREFIX:")[1].trim();
            context.forward("logProcessor".getBytes(), input.getBytes());
        }
    }

    public void punctuate(long timestamp) {
    }

    public void close() {
    }
}
