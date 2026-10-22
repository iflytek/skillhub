package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.email.delivery.NotificationEmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
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
            boolean connectFailure = ex.getFailedMessages().isEmpty() && hasConnectFailure(ex);
            return connectFailure ? Result.TEMPORARY_FAILURE : Result.UNKNOWN;
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

    private boolean hasConnectFailure(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause())
            if (cause instanceof java.net.ConnectException || cause instanceof java.net.UnknownHostException) {
                return true;
            }
        return false;
    }
}
