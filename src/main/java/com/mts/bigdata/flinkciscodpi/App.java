/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.company.bigdata.flinkciscodpi;


import com.company.bigdata.dataflow.clicks.model.gen.TClickFlowEvent;
import com.company.bigdata.dataflow.clicks.model.gen.TClickHttpEvent;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.reflect.ReflectData;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.avro.ParquetAvroWriters;
import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.AnnotatedSecurityInfo;
import org.apache.hadoop.security.SaslRpcClient;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class App {
    public String getGreeting() {
        return "Hello world.";
    }

    public static Schema getSchema(Class source) {

        Schema schema = ReflectData.AllowNull.get().getSchema(source);
        String namespace = schema.getNamespace();

        List<Schema.Field> baseFields = schema.getFields().stream()
                .map(field -> new Schema.Field(field.name(), field.schema(), field.doc(), field.defaultVal()))
                .collect(Collectors.toList());

        baseFields.removeIf(i -> i.name().equals("__isset_bitfield"));

        /*if (schema != null)
            throw new java.lang.Error("this is very bad");*/

        return Schema.createRecord(schema.getName(), "new_schema", namespace, false, baseFields);
    }

    public static DataStream<GenericRecord> getStream(StreamExecutionEnvironment env, Topic topic, Properties properties, TBase source) {
        Schema schema = getSchema(source.getClass());
        DataStream<GenericRecord> stream = env
                .addSource(new FlinkKafkaConsumer<>(topic.getCode(), new AbstractDeserializationSchema<GenericRecord>() {
                    @Override
                    public GenericRecord deserialize(byte[] bytes) throws IOException {
//                        TClickFlowEvent flowEvent = new TClickFlowEvent();
                        TDeserializer td = new TDeserializer(new TCompactProtocol.Factory());

                        try {
                            td.deserialize(source, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).array());
                        } catch (TException e) {
                            e.printStackTrace();
                        }

                        Schema schema1 = getSchema(source.getClass());
                        GenericRecord genericRecord = new GenericData.Record(schema1);
                        for (int i = 0; i < schema1.getFields().size() - 1; i++)
                            genericRecord.put(i, source.getFieldValue(source.fieldForId(i + 1)));
                        return genericRecord;
                    }
                }, properties));

        StreamingFileSink<GenericRecord> sink = StreamingFileSink
                .forBulkFormat(new Path("hdfs://srv.ru:8020//user/user/flink_test"),
                        ParquetAvroWriters.forGenericRecord(schema))
                .withBucketAssigner(new CustomBucketAssigner<>(topic.name()))
                .build();

//        stream.addSink(sink);
//        stream.print();
        stream.map((MapFunction<GenericRecord, String>) str -> "Stream_" + topic + ": " + str).print();

        return stream;
    }

    public static void main(String[] args) {
        System.out.println(new App().getGreeting());

        JSONParser parser = new JSONParser();
        try {
            JSONObject jsonObject = (JSONObject)parser.parse(new FileReader("src/com/company/topics.json"));
            String name = (String) jsonObject.get("name");
            System.out.println(jsonObject.toJSONString());
        } catch (ParseException | FileNotFoundException e) {
            e.printStackTrace();
        }

        System.setProperty("java.security.auth.login.config","src/main/resources/jaas.conf");

        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://srv.ru:8020");
        conf.set("hadoop.security.authentication", "kerberos");

        System.out.println(conf);


        UserGroupInformation.setConfiguration(conf);
        try {
            UserGroupInformation.loginUserFromKeytab("user@msk.ru", "/home/user/user.keytab");
        } catch (IOException e) {
            e.printStackTrace();
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(100000);
        env.getCheckpointConfig().enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION);

        Properties properties = new Properties();

        try {
            properties.load(new FileInputStream(new File("src/main/resources/kafkaprops.conf")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(properties);

        TBase tBase = null;

        switch ("http") {
            case "flow":
                tBase = new TClickFlowEvent();
                break;
            case "http":
                tBase = new TClickHttpEvent();
                break;
            default:
                throw new java.lang.Error("Incorrect programm argument: " + args[0]);
        }

        Map<String, DataStream<GenericRecord>> streams = new HashMap<>();



        for (Topic t: Topic.values())
            streams.put(t.toString(), getStream(env, t, properties, tBase));

        System.out.println(streams);

        try {
            env.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
