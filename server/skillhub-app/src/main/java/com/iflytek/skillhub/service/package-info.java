/**
 * Application services act as workflow owners for controller-facing use cases.
 *
 * <p>They may coordinate domain services, domain repository ports, and
 * application query repositories, but they should not become the long-term home
 * of repeated read-model assembly logic. When a response requires repeated
 * multi-source joins, display-oriented projection, or snapshot-field recovery,
 * prefer moving that assembly into {@code com.iflytek.skillhub.repository}.
 */
package com.iflytek.skillhub.service;
