package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.email.delivery.NotificationEmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Conservative SMTP outcomes: a send exception can happen after DATA acceptance, so is UNKNOWN. */
@Component
public class SmtpNotificationEmailSender implements NotificationEmailSender {
    private final ObjectProvider<JavaMailSender> senders;
    private final String fromAddress;
    private final String fromName;
    public SmtpNotificationEmailSender(ObjectProvider<JavaMailSender> senders,
            @Value("${skillhub.notification.email.from-address:${spring.mail.username:}}") String fromAddress,
            @Value("${skillhub.notification.email.from-name:SkillHub}") String fromName) {
        this.senders = senders;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public Result send(String recipient, String locale, long deliveryId, String subject, String text) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || fromAddress.isBlank()) {
            return Result.PERMANENT_FAILURE;
        }
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, fromName, "UTF-8"));
            helper.setTo(new InternetAddress(recipient, true));
            helper.setSubject(subject);
            helper.setText(text, false);
            // Stable correlation, not an SMTP idempotency guarantee.
            message.setHeader("X-SkillHub-Delivery", Long.toString(deliveryId));
            String domain = new InternetAddress(fromAddress, true).getAddress().split("@", 2)[1];
            message.setHeader("Message-ID", "<skillhub-delivery-" + deliveryId + "@" + domain + ">");
            sender.send(message);
            return Result.ACCEPTED;
        } catch (MailAuthenticationException ex) {
            return Result.PERMANENT_FAILURE;
        } catch (MailSendException ex) {
            // Connection setup failure is known pre-DATA; arbitrary messaging errors remain uncertain.
            for (Exception failed : ex.getFailedMessages().values()) {
                Result rejected = rejection(failed);
                if (rejected != null) {
                    return rejected;
                }
            }
            Result rejected = rejection(ex);
            if (rejected != null) {
                return rejected;
            }
            return connectionSetupFailure(sender, ex) ? Result.TEMPORARY_FAILURE : Result.UNKNOWN;
        } catch (MailException ex) {
            return Result.UNKNOWN;
        } catch (MessagingException | java.io.UnsupportedEncodingException | IllegalArgumentException ex) {
            return Result.PERMANENT_FAILURE;
        }
    }

    private Result rejection(Throwable error) {
        for (Throwable cause = error; cause != null;
                cause = cause instanceof MessagingException messaging && messaging.getNextException() != null
                        ? messaging.getNextException()
                        : cause.getCause()) {
            int code = cause instanceof SMTPAddressFailedException address ? address.getReturnCode()
                    : cause instanceof SMTPSendFailedException send        ? send.getReturnCode()
                                                                           : 0;
            if (code >= 500 && code <= 599) {
                return Result.PERMANENT_FAILURE;
            }
            if (code >= 400 && code <= 499) {
                return Result.TEMPORARY_FAILURE;
            }
        }
        return null;
    }

    private boolean connectionSetupFailure(JavaMailSender sender, MailSendException failure) {
        Throwable setup = failure.getCause();
        // Spring doSend attaches the same connectTransport exception to every not-yet-sent message.
        // sendMessage failures instead have no top-level cause; close failures have an empty map or
        // a different cause. A nonempty failedMessages map alone never proves a pre-DATA failure.
        if (!(sender instanceof JavaMailSenderImpl) || setup == null || failure.getFailedMessages().isEmpty()
                || failure.getFailedMessages().values().stream().anyMatch(value -> value != setup))
            return false;
        var pending = new java.util.ArrayDeque<Throwable>();
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        pending.add(setup);
        while (!pending.isEmpty()) {
            var cause = pending.removeFirst();
            if (!visited.add(cause))
                continue;
            if (cause instanceof MailConnectException || cause instanceof java.net.ConnectException
                    || cause instanceof java.net.UnknownHostException
                    || cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.SocketException
                    || cause instanceof javax.net.ssl.SSLException)
                return true;
            if (cause.getCause() != null)
                pending.add(cause.getCause());
            if (cause instanceof MessagingException messaging && messaging.getNextException() != null)
                pending.add(messaging.getNextException());
        }
        return false;
    }
}
