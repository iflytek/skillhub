package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.event.UserActivatedEvent;
import com.iflytek.skillhub.domain.namespace.PersonalNamespaceOwner;
import com.iflytek.skillhub.domain.namespace.PersonalNamespaceProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Creates a newly activated account's own namespace once the account itself is committed.
 *
 * <p>Runs synchronously rather than on the event executor so the namespace exists by the time the
 * user's next request arrives, and swallows failures so a naming clash or a database hiccup costs
 * the user a namespace rather than their registration or login.
 */
@Component
public class PersonalNamespaceProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(PersonalNamespaceProvisioningListener.class);

    private final PersonalNamespaceProvisioningService personalNamespaceProvisioningService;

    public PersonalNamespaceProvisioningListener(
            PersonalNamespaceProvisioningService personalNamespaceProvisioningService) {
        this.personalNamespaceProvisioningService = personalNamespaceProvisioningService;
    }

    @TransactionalEventListener
    public void onUserActivated(UserActivatedEvent event) {
        try {
            personalNamespaceProvisioningService.provisionFor(
                    new PersonalNamespaceOwner(event.userId(), event.username(), event.email()));
        } catch (RuntimeException e) {
            log.warn("Personal namespace provisioning failed for user {}; the account is unaffected",
                    event.userId(), e);
        }
    }
}
