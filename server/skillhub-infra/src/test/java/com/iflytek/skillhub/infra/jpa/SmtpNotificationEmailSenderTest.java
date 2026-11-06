package com.iflytek.skillhub.infra.jpa;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.notification.email.delivery.NotificationEmailSender.Result;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.*;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
class SmtpNotificationEmailSenderTest {
    @SuppressWarnings("unchecked")
    @Test
    void preservesPlainTextAndStableCorrelationWithoutCallingMessagesDelivered() throws Exception {
        var mail = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(mail.createMimeMessage()).thenReturn(message);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail);
        var sender = new SmtpNotificationEmailSender(provider, "notify@example.test", "SkillHub");
        assertThat(sender.send("u@example.test", "en", 11, "Summary", "safe text")).isEqualTo(Result.ACCEPTED);
        assertThat(message.getHeader("Message-ID", null)).isEqualTo("<skillhub-delivery-11@example.test>");
        assertThat(message.getContentType()).doesNotContain("html");
        verify(mail).send(message);
    }
    @SuppressWarnings("unchecked")
    @Test
    void classifiesExplicitRejectionsAndAmbiguousPostDataErrorsConservatively() throws Exception {
        var mail = mock(JavaMailSender.class);
        when(mail.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail);
        var sender = new SmtpNotificationEmailSender(provider, "notify@example.test", "SkillHub");
        doThrow(new MailSendException(Map.of(
                        new Object(), new SMTPSendFailedException("RCPT", 550, "rejected", null, null, null, null))))
                .when(mail)
                .send(any(MimeMessage.class));
        assertThat(sender.send("u@example.test", "en", 1, "s", "t")).isEqualTo(Result.PERMANENT_FAILURE);
        doThrow(new MailSendException(Map.of(
                        new Object(), new SMTPSendFailedException("RCPT", 450, "try later", null, null, null, null))))
                .when(mail)
                .send(any(MimeMessage.class));
        assertThat(sender.send("u@example.test", "en", 1, "s", "t")).isEqualTo(Result.TEMPORARY_FAILURE);
        doThrow(new MailSendException("uncertain", new java.net.SocketTimeoutException()))
                .when(mail)
                .send(any(MimeMessage.class));
        assertThat(sender.send("u@example.test", "en", 1, "s", "t")).isEqualTo(Result.UNKNOWN);
        doThrow(new MailAuthenticationException("auth")).when(mail).send(any(MimeMessage.class));
        assertThat(sender.send("u@example.test", "en", 1, "s", "t")).isEqualTo(Result.PERMANENT_FAILURE);
    }
    private SmtpNotificationEmailSender component(JavaMailSender mail) {
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        beans.addBean("mailSender", mail);
        return new SmtpNotificationEmailSender(
                beans.getBeanProvider(JavaMailSender.class), "notify@example.test", "SkillHub");
    }

    private org.springframework.mail.javamail.JavaMailSenderImpl networkMail(int port) {
        var mail = new org.springframework.mail.javamail.JavaMailSenderImpl();
        mail.setHost("127.0.0.1");
        mail.setPort(port);
        mail.getJavaMailProperties().setProperty("mail.smtp.localhost", "localhost");
        mail.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "300");
        mail.getJavaMailProperties().setProperty("mail.smtp.timeout", "300");
        mail.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "300");
        return mail;
    }

    private static org.eclipse.angus.mail.util.MailConnectException connectionFailure(Exception cause) {
        return new org.eclipse.angus.mail.util.MailConnectException(
                new org.eclipse.angus.mail.util.SocketConnectException(
                        "test setup failure", cause, "local.test", 25, 300));
    }

    private org.springframework.mail.javamail.JavaMailSenderImpl connectFailureMail(
            jakarta.mail.MessagingException failure) {
        return new org.springframework.mail.javamail.JavaMailSenderImpl() {
            @Override
            protected jakarta.mail.Transport connectTransport() throws jakarta.mail.MessagingException {
                throw failure;
            }
        };
    }

    @Test
    void actualSpringConnectionRefusalWithNonemptyFailedMessagesIsTemporary() throws Exception {
        // Reserve an unconnected TCP socket: its port cannot be stolen, and no server is listening there.
        try (var reserved = new java.net.Socket()) {
            reserved.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
            var mail = networkMail(reserved.getLocalPort());
            MailSendException error = org.junit.jupiter.api.Assertions.assertThrows(
                    MailSendException.class, () -> mail.send(mail.createMimeMessage()));
            assertThat(error.getFailedMessages()).hasSize(1);
            assertThat(error.getCause()).isInstanceOf(org.eclipse.angus.mail.util.MailConnectException.class);
            assertThat(error.getFailedMessages().values()).allMatch(value -> value == error.getCause());
            assertThat(component(mail).send("u@example.test", "en", 1, "s", "t")).isEqualTo(Result.TEMPORARY_FAILURE);
        }
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> setupFailures() {
        return java.util.stream.Stream.of(org.junit.jupiter.params.provider.Arguments.of("connect timeout",
                                                  connectionFailure(new java.net.SocketTimeoutException())),
                org.junit.jupiter.params.provider.Arguments.of(
                        "DNS", connectionFailure(new java.net.UnknownHostException())),
                org.junit.jupiter.params.provider.Arguments.of("greeting timeout",
                        new jakarta.mail.MessagingException("greeting", new java.net.SocketTimeoutException())),
                org.junit.jupiter.params.provider.Arguments.of("TLS handshake",
                        new jakarta.mail.MessagingException("TLS", new javax.net.ssl.SSLHandshakeException("test"))));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.MethodSource("setupFailures")
    void realSpringDoSendRetainsConnectionStageForTimeoutDnsAndHandshake(
            String description, jakarta.mail.MessagingException cause) {
        var mail = connectFailureMail(cause);
        MailSendException error = org.junit.jupiter.api.Assertions.assertThrows(
                MailSendException.class, () -> mail.send(mail.createMimeMessage()));
        assertThat(error.getFailedMessages()).hasSize(1);
        assertThat(error.getCause()).isSameAs(cause);
        assertThat(error.getFailedMessages().values()).containsExactly(cause);
        assertThat(component(mail).send("u@example.test", "en", 1, "s", "t")).isEqualTo(Result.TEMPORARY_FAILURE);
    }

    private static final class FailingTransport extends jakarta.mail.Transport {
        final jakarta.mail.MessagingException sendFailure;
        final jakarta.mail.MessagingException closeFailure;
        int messages;
        FailingTransport(jakarta.mail.MessagingException sendFailure, jakarta.mail.MessagingException closeFailure) {
            super(Session.getInstance(new Properties()), null);
            this.sendFailure = sendFailure;
            this.closeFailure = closeFailure;
        }
        @Override
        public boolean isConnected() {
            return true;
        }
        @Override
        public void sendMessage(jakarta.mail.Message message, jakarta.mail.Address[] recipients)
                throws jakarta.mail.MessagingException {
            messages++;
            if (sendFailure != null)
                throw sendFailure;
        }
        @Override
        public void close() throws jakarta.mail.MessagingException {
            if (closeFailure != null)
                throw closeFailure;
        }
    }

    private org.springframework.mail.javamail.JavaMailSenderImpl transportMail(FailingTransport transport) {
        return new org.springframework.mail.javamail.JavaMailSenderImpl() {
            @Override
            protected jakarta.mail.Transport connectTransport() {
                return transport;
            }
        };
    }

    static java.util.stream.Stream<jakarta.mail.MessagingException> uncertainSendFailures() {
        return java.util.stream.Stream.of(
                new jakarta.mail.MessagingException("waiting for DATA response", new java.net.SocketTimeoutException()),
                new jakarta.mail.MessagingException("send stage", new java.net.ConnectException()),
                connectionFailure(new java.net.SocketTimeoutException()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("uncertainSendFailures")
    void actualSpringSendStageFailuresRemainUnknownEvenWithNonemptyFailedMessages(
            jakarta.mail.MessagingException failure) {
        var transport = new FailingTransport(failure, null);
        var mail = transportMail(transport);
        MailSendException error = org.junit.jupiter.api.Assertions.assertThrows(
                MailSendException.class, () -> mail.send(mail.createMimeMessage()));
        assertThat(error.getFailedMessages()).hasSize(1);
        assertThat(error.getCause()).isNull();
        assertThat(component(mail).send("u@example.test", "en", 1, "s", "t")).isEqualTo(Result.UNKNOWN);
        assertThat(transport.messages).isEqualTo(2);
    }

    @Test
    void actualSpringCloseFailureAfterSuccessfulSendIsUnknown() {
        var transport = new FailingTransport(
                null, new jakarta.mail.MessagingException("close", new java.net.SocketTimeoutException()));
        var mail = transportMail(transport);
        assertThat(component(mail).send("u@example.test", "en", 1, "s", "t")).isEqualTo(Result.UNKNOWN);
        assertThat(transport.messages).isEqualTo(1);
    }

    @Test
    void actualSpringCloseFailureCannotTurnAnUncertainSendIntoTemporary() {
        var sendFailure = new jakarta.mail.MessagingException("DATA response", new java.net.SocketTimeoutException());
        var closeFailure = connectionFailure(new java.net.ConnectException());
        var transport = new FailingTransport(sendFailure, closeFailure);
        var mail = transportMail(transport);
        MailSendException error = org.junit.jupiter.api.Assertions.assertThrows(
                MailSendException.class, () -> mail.send(mail.createMimeMessage()));
        assertThat(error.getCause()).isSameAs(closeFailure);
        assertThat(error.getFailedMessages().values()).containsExactly(sendFailure);
        assertThat(component(mail).send("u@example.test", "en", 1, "s", "t")).isEqualTo(Result.UNKNOWN);
    }

    @Test
    void actualSmtpGreetingReadTimeoutIsPreMessageAndTemporary() throws Exception {
        try (var listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
                var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            listener.setSoTimeout(3000);
            var release = new java.util.concurrent.CountDownLatch(1);
            var accepted = pool.submit(() -> {
                try (var socket = listener.accept()) {
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        throw new AssertionError("test did not finish");
                }
                return null;
            });
            try {
                assertThat(component(networkMail(listener.getLocalPort())).send("u@example.test", "en", 1, "s", "t"))
                        .isEqualTo(Result.TEMPORARY_FAILURE);
            } finally {
                release.countDown();
            }
            accepted.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    @Test
    void actualSmtpMissingResponseAfterCompleteDataRemainsUnknown() throws Exception {
        try (var listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
                var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            listener.setSoTimeout(3000);
            var release = new java.util.concurrent.CountDownLatch(1);
            var dataReceived = new java.util.concurrent.atomic.AtomicBoolean();
            var accepted = pool.submit(() -> {
                try (var socket = listener.accept()) {
                    socket.setSoTimeout(3000);
                    var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                            socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
                    var writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(),
                                                                 java.nio.charset.StandardCharsets.US_ASCII),
                            true);
                    writer.print("220 localhost test SMTP\r\n");
                    writer.flush();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("EHLO") || line.startsWith("HELO"))
                            writer.print("250 localhost\r\n");
                        else if (line.startsWith("MAIL FROM") || line.startsWith("RCPT TO"))
                            writer.print("250 OK\r\n");
                        else if (line.equals("DATA")) {
                            writer.print("354 End with dot\r\n");
                            writer.flush();
                            while ((line = reader.readLine()) != null && !line.equals(".")) {
                            }
                            if (line == null)
                                throw new AssertionError("client disconnected before complete DATA");
                            dataReceived.set(true);
                            // The server received DATA but does not return acceptance. It might have queued it.
                            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                                throw new AssertionError("test did not finish");
                            return null;
                        } else
                            throw new AssertionError("Unexpected SMTP command");
                        writer.flush();
                    }
                    throw new AssertionError("No DATA received");
                }
            });
            try {
                assertThat(component(networkMail(listener.getLocalPort()))
                                   .send("u@example.test", "en", 1, "s", "safe text"))
                        .isEqualTo(Result.UNKNOWN);
                assertThat(dataReceived).isTrue();
            } finally {
                release.countDown();
            }
            accepted.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
