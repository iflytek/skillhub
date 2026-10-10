package com.iflytek.skillhub.observability.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.logstash.logback.composite.GlobalCustomFieldsJsonProvider;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggerNameJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventFormattedTimestampJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.composite.loggingevent.LoggingEventThreadNameJsonProvider;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import net.logstash.logback.composite.loggingevent.StackTraceJsonProvider;
import net.logstash.logback.composite.loggingevent.ThrowableClassNameJsonProvider;
import net.logstash.logback.composite.loggingevent.ThrowableMessageJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;

/**
 * ECS-style JSON encoder with an explicit field allowlist.
 */
public class SkillHubEcsEncoder extends LoggingEventCompositeJsonEncoder {

    private static final String ECS_VERSION = "1.2.0";

    private String serviceName = "skillhub";
    private String serviceVersion = "unknown";
    private String serviceEnvironment = "local";
    private boolean externalTraceIdEnabled;

    @Override
    public void start() {
        if (isStarted()) {
            return;
        }
        setLineSeparator("UNIX");
        setProviders(createProviders());
        super.start();
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public void setServiceEnvironment(String serviceEnvironment) {
        this.serviceEnvironment = serviceEnvironment;
    }

    public void setTracingMode(String tracingMode) {
        this.externalTraceIdEnabled = "external-agent".equalsIgnoreCase(tracingMode);
    }

    private LoggingEventJsonProviders createProviders() {
        LoggingEventJsonProviders providers = new LoggingEventJsonProviders();

        LoggingEventFormattedTimestampJsonProvider timestamp =
                new LoggingEventFormattedTimestampJsonProvider();
        timestamp.setFieldName("@timestamp");
        timestamp.setTimeZone("UTC");
        providers.addTimestamp(timestamp);

        LogLevelJsonProvider level = new LogLevelJsonProvider();
        level.setFieldName("log.level");
        providers.addLogLevel(level);

        MessageJsonProvider message = new MessageJsonProvider();
        message.setFieldName("message");
        providers.addMessage(message);

        LoggerNameJsonProvider logger = new LoggerNameJsonProvider();
        logger.setFieldName("log.logger");
        providers.addLoggerName(logger);

        LoggingEventThreadNameJsonProvider thread = new LoggingEventThreadNameJsonProvider();
        thread.setFieldName("process.thread.name");
        providers.addThreadName(thread);

        providers.addGlobalCustomFields(serviceFields());
        providers.addProvider(new CorrelationJsonProvider(externalTraceIdEnabled));

        ThrowableClassNameJsonProvider errorType = new ThrowableClassNameJsonProvider();
        errorType.setFieldName("error.type");
        errorType.setUseSimpleClassName(false);
        providers.addThrowableClassName(errorType);

        ThrowableMessageJsonProvider errorMessage = new ThrowableMessageJsonProvider();
        errorMessage.setFieldName("error.message");
        providers.addThrowableMessage(errorMessage);

        StackTraceJsonProvider stackTrace = new StackTraceJsonProvider();
        stackTrace.setFieldName("error.stack_trace");
        providers.addStackTrace(stackTrace);

        return providers;
    }

    private GlobalCustomFieldsJsonProvider<ILoggingEvent> serviceFields() {
        ObjectNode fields = JsonNodeFactory.instance.objectNode();
        fields.put("ecs.version", ECS_VERSION);
        fields.put("service.name", serviceName);
        fields.put("service.version", serviceVersion);
        fields.put("service.environment", serviceEnvironment);
        fields.put("event.dataset", serviceName);

        GlobalCustomFieldsJsonProvider<ILoggingEvent> provider =
                new GlobalCustomFieldsJsonProvider<>();
        provider.setCustomFieldsNode(fields);
        return provider;
    }
}
