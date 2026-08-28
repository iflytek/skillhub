package com.iflytek.skillhub.auth.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for AD objectGUID binary normalization.
 *
 * <p>Active Directory stores objectGUID as a 16-byte mixed-endian OctetString. Before the fix,
 * {@code getAttributeValue} called {@code toString()} on the {@code byte[]} returned by JNDI,
 * producing an unstable {@code "[B@<identityHashCode>"} that changed on every login and caused
 * each login to provision a brand-new account. These tests lock in the stable canonical-GUID
 * conversion via reflection on the private {@code toStableGuidString} helper.
 */
class LdapSubjectGuidTest {

    private static String invoke(byte[] bytes) throws Exception {
        Method m = LdapAuthService.class.getDeclaredMethod("toStableGuidString", byte[].class);
        m.setAccessible(true);
        return (String) m.invoke(null, bytes);
    }

    @Test
    void objectGuid_byteArray_isStableCanonicalGuid() throws Exception {
        // AD objectGUID {0x8b,0xe3,0x9d,0x4c,...} little-endian -> 4c9de38b-...
        // The same logical GUID must always serialize to the same string regardless of which
        // byte[] instance JNDI handed back, so repeat-login identity matching stays stable.
        byte[] guid = new byte[]{
            (byte) 0x8b, (byte) 0xe3, (byte) 0x9d, 0x4c,   // LE uint32 -> 4c9de38b
            (byte) 0xb5, 0x55,                       // LE uint16 -> 55b5
            (byte) 0xe8, 0x42,                       // LE uint16 -> 42e8
            (byte) 0x8e, 0x2f,                       // big-endian -> 8e2f
            (byte) 0x9a, 0x41, (byte) 0xc3, 0x77, (byte) 0xa6, 0x71  // node -> 9a41c377a671
        };

        String first = invoke(guid);
        String second = invoke(guid.clone()); // different instance, same bytes

        assertThat(first).isEqualTo("4c9de38b-55b5-42e8-8e2f-9a41c377a671");
        assertThat(second).isEqualTo(first); // deterministic across instances
    }

    @Test
    void nonGuidByteArray_fallsBackToDeterministicHex() throws Exception {
        byte[] sid = new byte[]{0x01, 0x00, 0x04, (byte) 0x80, 0x14, 0x00, 0x00, 0x00};

        String first = invoke(sid);
        String second = invoke(sid.clone());

        assertThat(first).isEqualTo("0100048014000000");
        assertThat(second).isEqualTo(first); // stable even for non-GUID binaries
    }

    @Test
    void sameGuid_differentInstances_produceSameString() throws Exception {
        // The core regression: two independent byte[] with identical content must yield identical
        // strings, proving identity matching will be stable across LDAP connections.
        byte[] a = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        byte[] b = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

        assertThat(invoke(a)).isEqualTo(invoke(b));
    }
}
