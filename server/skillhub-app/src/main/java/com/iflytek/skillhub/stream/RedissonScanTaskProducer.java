package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class RedissonScanTaskProducer implements ScanTaskProducer {

    private static final Logger log = LoggerFactory.getLogger(RedissonScanTaskProducer.class);

    private final RedissonClient redissonClient;
    private final String streamKey;

    public RedissonScanTaskProducer(RedissonClient redissonClient, String streamKey) {
        this.redissonClient = redissonClient;
        this.streamKey = streamKey;
    }

    @Override
    public void publishScanTask(ScanTask task) {
        Map<String, String> fields = new HashMap<>();
        fields.put("taskId", task.taskId());
        fields.put("versionId", String.valueOf(task.versionId()));
        if (task.skillPath() != null && !task.skillPath().isBlank()) {
            fields.put("skillPath", task.skillPath());
        }
        if (task.bundleKey() != null && !task.bundleKey().isBlank()) {
            fields.put("bundleKey", task.bundleKey());
        }
        fields.put("publisherId", task.publisherId() != null ? task.publisherId() : "");
        fields.put("createdAtMillis", String.valueOf(task.createdAtMillis()));
        if (task.metadata() != null) {
            fields.putAll(task.metadata());
        }

        RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
        StreamMessageId messageId = stream.add(StreamAddArgs.entries(fields));
        log.info("Published scan task: taskId={}, versionId={}, bundleKey={}, hasSkillPath={}, recordId={}",
                task.taskId(), task.versionId(), task.bundleKey(), task.skillPath() != null && !task.skillPath().isBlank(), messageId);
    }
}
