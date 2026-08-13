package com.iflytek.skillhub.domain.namespace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalNamespaceNamingTest {

    private static final PersonalNamespaceOwner ALICE =
            new PersonalNamespaceOwner("usr_0f2a", "Alice.Wang", "alice.wang@example.com");

    @Test
    void rendersEachSupportedPlaceholder() {
        assertEquals("Alice.Wang", PersonalNamespaceNaming.render("${username}", ALICE));
        assertEquals("alice.wang", PersonalNamespaceNaming.render("${email_prefix}", ALICE));
        assertEquals("usr_0f2a", PersonalNamespaceNaming.render("${user_id}", ALICE));
    }

    @Test
    void leavesUnknownPlaceholdersInPlaceSoTyposAreVisible() {
        assertEquals("Alice.Wang-${nickname}", PersonalNamespaceNaming.render("${username}-${nickname}", ALICE));
    }

    @Test
    void slugBaseAppliesSlugCharacterRules() {
        assertEquals("alice-wang", PersonalNamespaceNaming.slugBase("${username}", ALICE));
        assertEquals("alice-wang-space", PersonalNamespaceNaming.slugBase("${username}-space", ALICE));
    }

    @Test
    void slugBaseRewritesUnderscoresBecauseSlugsDisallowThem() {
        String slug = PersonalNamespaceNaming.slugBase("${username}_space", ALICE);

        assertEquals("alice-wang-space", slug);
        assertTrue(SlugValidator.isValid(slug));
    }

    @Test
    void slugBaseFallsBackToEmailPrefixWhenUsernameIsMissing() {
        PersonalNamespaceOwner noUsername = new PersonalNamespaceOwner("usr_1", null, "bob@example.com");

        assertEquals("bob", PersonalNamespaceNaming.slugBase("${username}", noUsername));
    }

    @Test
    void slugBaseFallsBackToUserIdWhenTemplateRendersNothingUsable() {
        PersonalNamespaceOwner anonymous = new PersonalNamespaceOwner("usr_abc123", null, null);

        assertEquals("usr-abc123", PersonalNamespaceNaming.slugBase("${username}", anonymous));
    }

    @Test
    void slugBaseLeavesRoomForADeduplicationSuffix() {
        PersonalNamespaceOwner longName = new PersonalNamespaceOwner("usr_1", "a".repeat(200), null);

        String base = PersonalNamespaceNaming.slugBase("${username}", longName);

        assertTrue(base.length() <= 59, "base was " + base.length() + " chars");
        assertTrue(SlugValidator.isValid(base + "-64"));
    }

    @Test
    void displayNameFallsBackToTheSlugWhenTemplateRendersBlank() {
        PersonalNamespaceOwner noEmail = new PersonalNamespaceOwner("usr_1", "alice", null);

        assertEquals("chosen-slug",
                PersonalNamespaceNaming.displayName("${email_prefix}", noEmail, "chosen-slug"));
    }

    @Test
    void usernameFallsBackToTheUserIdWhenNothingElseIsKnown() {
        PersonalNamespaceOwner anonymous = new PersonalNamespaceOwner("usr_1", null, null);

        assertEquals("usr_1", PersonalNamespaceNaming.render("${username}", anonymous));
    }

    @Test
    void displayNameKeepsHumanReadableCharacters() {
        assertEquals("Alice.Wang's space",
                PersonalNamespaceNaming.displayName("${username}'s space", ALICE, "alice-wang"));
    }
}
