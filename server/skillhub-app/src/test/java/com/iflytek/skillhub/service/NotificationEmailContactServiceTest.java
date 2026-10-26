package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.domain.user.*;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.*;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class NotificationEmailContactServiceTest {
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final NotificationEmailContactRepository contacts = mock(NotificationEmailContactRepository.class);
    private final NotificationEmailPreferenceRepository preferences = mock(NotificationEmailPreferenceRepository.class);
    private final NotificationEmailPreferenceService modes = new NotificationEmailPreferenceService(preferences);
    private final NotificationEmailSettingsService settings = mock(NotificationEmailSettingsService.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final NotificationEmailCancellation cancellation = mock(NotificationEmailCancellation.class);
    private final UserAccount user = new UserAccount("user", "Name", "a@test.com", null);
    private final NotificationEmailContact contact = new NotificationEmailContact("user");
    private NotificationEmailContactService service;
    @BeforeEach
    void setup() {
        when(transactions.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        when(accounts.findById("user")).thenReturn(Optional.of(user));
        when(accounts.findLockedFreshById("user")).thenReturn(Optional.of(user));
        when(contacts.findById("user")).thenReturn(Optional.of(contact));
        when(contacts.lockOrCreate("user")).thenReturn(contact);
        when(settings.current()).thenReturn(new NotificationEmailPolicy(true, 15, "http://localhost", 0));
        when(settings.lockCurrent()).thenReturn(new NotificationEmailPolicy(true, 15, "http://localhost", 0));
        when(settings.smtpConfigured()).thenReturn(true);
        when(encoder.encode(anyString())).thenAnswer(i -> "hash:" + i.getArgument(0));
        when(encoder.matches(anyString(), anyString()))
                .thenAnswer(i -> ("hash:" + i.getArgument(0)).equals(i.getArgument(1)));
        service = new NotificationEmailContactService(accounts, contacts, preferences, modes, settings, sender, encoder,
                transactions, java.time.Clock.systemUTC(), cancellation, "noreply@test.com", "SkillHub");
    }
    @Test
    void challengeCommitsBeforeSmtpAndConsumesOnce() {
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(i -> {
            SimpleMailMessage message = i.getArgument(0);
            assertThat(message.getTo()).containsExactly("a@test.com");
            code.set(message.getText().replaceAll("(?s).*code: ([0-9]{6}).*", "$1"));
            return null;
        })
                .when(sender)
                .send(any(SimpleMailMessage.class));
        service.requestCode("user", "en");
        var order = inOrder(transactions, sender);
        order.verify(transactions).getTransaction(any());
        order.verify(transactions).commit(any());
        order.verify(sender).send(any(SimpleMailMessage.class));
        assertThat(service.verify("user", code.get()).verified()).isTrue();
        assertThatThrownBy(() -> service.verify("user", code.get()))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        assertThat(service.current("user")).isPresent();
        user.setEmail("b@test.com");
        user.setEmail("a@test.com");
        assertThat(service.current("user")).isEmpty();
    }
    @Test
    void wrongCodeAttemptsPersistBeforeError() {
        service.requestCode("user", "en");
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(() -> service.verify("user", "bad-code"))
                    .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        assertThat(contact.accepts(user.getEmail(), user.getEmailRevision(), java.time.Instant.now())).isFalse();
        verify(contacts, times(6)).save(contact);
    }
    @Test
    void smtpFailureDoesNotCreateProof() {
        doThrow(new RuntimeException("smtp failed")).when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> service.requestCode("user", "en"))
                .isInstanceOf(com.iflytek.skillhub.auth.exception.AuthFlowException.class);
        assertThat(service.current("user")).isEmpty();
        assertThat(contact.getChallengeHash()).isNull();
    }
    @Test
    void defaultsOffAndDuplicateCategoryRejectedBeforeLock() {
        assertThat(service.preferences("user")).hasSize(4).allMatch(p -> p.mode().equals("OFF"));
        var pref = new NotificationEmailPreferenceResponse("REVIEW", "DIGEST");
        assertThatThrownBy(()
                                   -> service.updatePreferences(
                                           "user", new NotificationEmailPreferenceUpdateRequest(List.of(pref, pref))))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        verify(settings, never()).lockCurrent();
    }
    @Test
    void cannotOptInWithoutCurrentProof() {
        assertThatThrownBy(()
                                   -> service.updatePreferences("user",
                                           new NotificationEmailPreferenceUpdateRequest(List.of(
                                                   new NotificationEmailPreferenceResponse("REVIEW", "IMMEDIATE")))))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        verify(preferences, never()).save(any());
    }
    @Test
    void revisionChangesOnlyForNormalizedAddressChange() {
        assertThat(user.getEmailRevision()).isZero();
        user.setEmail(" A@TEST.COM ");
        assertThat(user.getEmailRevision()).isZero();
        user.setEmail("b@test.com");
        user.setEmail("a@test.com");
        assertThat(user.getEmailRevision()).isEqualTo(2);
    }
    @Test
    void longAccountIdStillRequiresCorrectCodeWithRealBcrypt() {
        String id = "u".repeat(128);
        var longUser = new UserAccount(id, "Long", "long@test.com", null);
        var longContact = new NotificationEmailContact(id);
        when(accounts.findById(id)).thenReturn(Optional.of(longUser));
        when(accounts.findLockedFreshById(id)).thenReturn(Optional.of(longUser));
        when(contacts.findById(id)).thenReturn(Optional.of(longContact));
        when(contacts.lockOrCreate(id)).thenReturn(longContact);
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            code.set(message.getText().replaceAll("(?s).*code: ([0-9]{6}).*", "$1"));
            return null;
        })
                .when(sender)
                .send(any(SimpleMailMessage.class));
        var bcryptService = new NotificationEmailContactService(accounts, contacts, preferences, modes, settings,
                sender, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4), transactions,
                java.time.Clock.systemUTC(), cancellation, "noreply@test.com", "SkillHub");
        bcryptService.requestCode(id, "en");
        String wrong = code.get().equals("000000") ? "000001" : "000000";
        assertThatThrownBy(() -> bcryptService.verify(id, wrong))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        assertThat(bcryptService.verify(id, code.get()).verified()).isTrue();
    }
    @Test
    void passwordResetHashCannotBeConsumedAsNotificationProof() {
        var bcrypt = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        contact.challenge("reset-copy", bcrypt.encode("123456"), user.getEmail(), 0, "en", java.time.Instant.now());
        var realService = new NotificationEmailContactService(accounts, contacts, preferences, modes, settings, sender,
                bcrypt, transactions, java.time.Clock.systemUTC(), cancellation, "noreply@test.com", "SkillHub");
        assertThatThrownBy(() -> realService.verify("user", "123456"))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        assertThat(realService.current("user")).isEmpty();
    }
    @Test
    void anotherAccountCannotConsumeCopiedChallengeHash() {
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            code.set(message.getText().replaceAll("(?s).*code: ([0-9]{6}).*", "$1"));
            return null;
        })
                .when(sender)
                .send(any(SimpleMailMessage.class));
        service.requestCode("user", "en");
        var otherUser = new UserAccount("other", "Other", "a@test.com", null);
        var otherContact = new NotificationEmailContact("other");
        otherContact.challenge("copied", contact.getChallengeHash(), "a@test.com", 0, "en", java.time.Instant.now());
        when(accounts.findById("other")).thenReturn(Optional.of(otherUser));
        when(accounts.findLockedFreshById("other")).thenReturn(Optional.of(otherUser));
        when(contacts.findById("other")).thenReturn(Optional.of(otherContact));
        when(contacts.lockOrCreate("other")).thenReturn(otherContact);
        assertThatThrownBy(() -> service.verify("other", code.get()))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        assertThat(service.current("other")).isEmpty();
    }
}
