package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.observability.MessageCarrierAdapter;

import java.util.Map;

/**
 * Adapts Redis Stream field maps to the transport-neutral message observation boundary.
 *
 * <p>Redis Stream entries do not have a separate header collection, so reserved propagation fields
 * share the entry map with business fields. {@code MessageObservationSupport} owns and sanitizes
 * those reserved fields; business payload types remain unaware of them.</p>
 */
final class RedisStreamMessageCarrier {

    static final String MESSAGING_SYSTEM = "redis";

    static final MessageCarrierAdapter<Map<String, String>> ADAPTER =
            new MessageCarrierAdapter<>() {
                @Override
                public String get(Map<String, String> carrier, String key) {
                    return carrier.get(key);
                }

                @Override
                public void set(Map<String, String> carrier, String key, String value) {
                    carrier.put(key, value);
                }

                @Override
                public void remove(Map<String, String> carrier, String key) {
                    carrier.remove(key);
                }
            };

    private RedisStreamMessageCarrier() {
    }
}
