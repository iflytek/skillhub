package com.iflytek.skillhub.auth.federation.core;

import java.util.List;
import java.util.Objects;

/** Closed set of values that can cross the authentication adapter boundary. */
public sealed interface AttributeValue
        permits AttributeValue.Text, AttributeValue.TextList, AttributeValue.Flag, AttributeValue.NumberValue {

    record Text(String value) implements AttributeValue {
        public Text {
            Objects.requireNonNull(value, "attribute text must not be null");
        }
    }

    record TextList(List<String> values) implements AttributeValue {
        public TextList {
            Objects.requireNonNull(values, "attribute text list must not be null");
            if (values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("attribute text list must not contain null values");
            }
            values = List.copyOf(values);
        }
    }

    record Flag(boolean value) implements AttributeValue {
    }

    record NumberValue(long value) implements AttributeValue {
    }
}
