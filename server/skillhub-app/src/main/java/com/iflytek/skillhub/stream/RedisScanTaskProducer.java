package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

public class RedisScanTaskProducer implements ScanTaskProducer {

    private static final Logger log = LoggerFactory.getLogger(RedisScanTaskProducer.class);

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;

    public RedisScanTaskProducer(StringRedisTemplate redisTemplate, String streamKey) {
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
    }

    @Override
    public void publishScanTask(ScanTask task) {
        Map<String, String> fields = new HashMap<>();
        fields.put("taskId", task.taskId());
        fields.put("versionId", String.valueOf(task.versionId()));
        fields.put("skillPath", task.skillPath());
        fields.put("publisherId", task.publisherId() != null ? task.publisherId() : "");
        fields.put("createdAtMillis", String.valueOf(task.createdAtMillis()));
        if (task.metadata() != null) {
            fields.putAll(task.metadata());
        }

        StringRecord record = StreamRecords.string(fields).withStreamKey(streamKey);
        RecordId recordId = redisTemplate.opsForStream().add(record);
        log.info("Published scan task: taskId={}, versionId={}, recordId={}",
                task.taskId(), task.versionId(), recordId);
    }
}
