package com.iflytek.skillhub.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class S3StoragePropertiesTest {

    @Test
    void gettersAndSetters_coverAllFields() {
        S3StorageProperties props = new S3StorageProperties();
        props.setEndpoint("http://minio:9000");
        props.setPublicEndpoint("http://public:9000");
        props.setBucket("mybucket");
        props.setAccessKey("access");
        props.setSecretKey("secret");
        props.setRegion("us-west-1");
        props.setForcePathStyle(false);
        props.setAutoCreateBucket(true);
        props.setPresignExpiry(Duration.ofMinutes(5));
        props.setMaxConnections(50);
        props.setConnectionAcquisitionTimeout(Duration.ofSeconds(1));
        props.setApiCallAttemptTimeout(Duration.ofSeconds(5));
        props.setApiCallTimeout(Duration.ofSeconds(15));

        assertThat(props.getEndpoint()).isEqualTo("http://minio:9000");
        assertThat(props.getPublicEndpoint()).isEqualTo("http://public:9000");
        assertThat(props.getBucket()).isEqualTo("mybucket");
        assertThat(props.getAccessKey()).isEqualTo("access");
        assertThat(props.getSecretKey()).isEqualTo("secret");
        assertThat(props.getRegion()).isEqualTo("us-west-1");
        assertThat(props.isForcePathStyle()).isFalse();
        assertThat(props.isAutoCreateBucket()).isTrue();
        assertThat(props.getPresignExpiry()).isEqualTo(Duration.ofMinutes(5));
        assertThat(props.getMaxConnections()).isEqualTo(50);
        assertThat(props.getConnectionAcquisitionTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(props.getApiCallAttemptTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.getApiCallTimeout()).isEqualTo(Duration.ofSeconds(15));
    }
}
