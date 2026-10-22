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
}
