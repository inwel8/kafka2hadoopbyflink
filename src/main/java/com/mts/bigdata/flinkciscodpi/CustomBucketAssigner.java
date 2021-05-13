package com.company.bigdata.flinkciscodpi;

import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.streaming.api.functions.sink.filesystem.BucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.SimpleVersionedStringSerializer;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CustomBucketAssigner<IN> implements BucketAssigner<IN, String> {

    private String topic;

    public CustomBucketAssigner(String topic) {
        this.topic = topic;
    }

    @Override
    public String getBucketId(IN in, Context context) {
        return "region=" + topic + "/loading_d=" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    @Override
    public SimpleVersionedSerializer<String> getSerializer() {
        return SimpleVersionedStringSerializer.INSTANCE;
    }
}
