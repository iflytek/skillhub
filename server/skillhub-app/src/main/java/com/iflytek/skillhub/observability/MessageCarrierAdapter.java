package com.iflytek.skillhub.observability;

import io.micrometer.observation.transport.Propagator;

/**
 * Adapts transport-specific message headers to the common observation boundary.
 *
 * <p>Implementations should operate on a transport envelope or header collection, never on a
 * business DTO. Micrometer uses the inherited getter and setter to extract and inject propagation
 * fields without exposing a concrete tracing implementation to the transport.</p>
 */
public interface MessageCarrierAdapter<C>
        extends Propagator.Getter<C>, Propagator.Setter<C> {

    /**
     * Removes every value associated with a transport header before trusted context is injected.
     */
    void remove(C carrier, String key);
}
