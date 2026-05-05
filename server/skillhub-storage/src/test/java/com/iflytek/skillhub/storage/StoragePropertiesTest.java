package com.iflytek.skillhub.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoragePropertiesTest {

    @Test
    void gettersAndSetters_coverAllFields() {
        StorageProperties props = new StorageProperties();
        props.setProvider("s3");
        StorageProperties.LocalProperties local = new StorageProperties.LocalProperties();
        local.setBasePath("/tmp/storage");
        props.setLocal(local);

        assertThat(props.getProvider()).isEqualTo("s3");
        assertThat(props.getLocal().getBasePath()).isEqualTo("/tmp/storage");
    }
}
