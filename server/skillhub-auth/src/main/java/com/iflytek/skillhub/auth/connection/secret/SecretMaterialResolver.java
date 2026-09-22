package com.iflytek.skillhub.auth.connection.secret;

import java.time.Instant;

/** Runtime-only Secret material boundary used by protocol Adapters. */
public interface SecretMaterialResolver {

    SecretMaterial resolve(
            SecretReference reference,
            SecretPurpose expectedPurpose,
            Instant now
    );
}
