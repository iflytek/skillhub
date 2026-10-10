#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$script_dir/.." && pwd)"

require_rg() {
    if ! command -v rg >/dev/null 2>&1; then
        echo "enterprise identity architecture check requires rg" >&2
        exit 2
    fi
}

report_matches() {
    local label="$1"
    local directory="$2"
    local pattern="$3"
    local matches

    if [[ ! -d "$directory" ]]; then
        return 0
    fi

    matches="$(rg -n --glob '*.java' --regexp "$pattern" "$directory" || true)"
    if [[ -z "$matches" ]]; then
        return 0
    fi

    echo "[$label]" >&2
    echo "$matches" >&2
    return 1
}

check_tree() {
    local root="$1"
    local violations=0
    local domain_root="$root/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain"
    local auth_root="$root/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth"
    local protected_domain
    local protected_core
    local adapter_root

    for protected_domain in organization directory entitlement; do
        report_matches \
            "domain-$protected_domain-forbidden-dependency" \
            "$domain_root/$protected_domain" \
            '^import com\.iflytek\.skillhub\.(auth|controller|compat|infra|repository|service|scim|oauth)\.' \
            || violations=$((violations + 1))
        report_matches \
            "domain-$protected_domain-protocol-leak" \
            "$domain_root/$protected_domain" \
            '(?i)\b(oidc|oauth|saml|scim|ldap|dingtalk|okta|azure|github|gitlab)\b' \
            || violations=$((violations + 1))
    done

    for protected_core in federation/core identity/core connection/core; do
        report_matches \
            "identity-core-concrete-adapter-import" \
            "$auth_root/$protected_core" \
            '^import .*\.adapter\.' \
            || violations=$((violations + 1))
        report_matches \
            "identity-core-provider-branch" \
            "$auth_root/$protected_core" \
            '(?i)\b(github|gitlab|dingtalk|okta|azure|keycloak|auth0)\b' \
            || violations=$((violations + 1))
    done

    for adapter_root in \
        "$auth_root/federation/adapter" \
        "$auth_root/connection/adapter"; do
        report_matches \
            "adapter-direct-state-write" \
            "$adapter_root" \
            '^import .*\.(UserAccountRepository|OrganizationMembershipRepository|NamespaceMemberRepository|NamespaceMemberGrantRepository);' \
            || violations=$((violations + 1))
        report_matches \
            "adapter-runtime-code-loading" \
            "$adapter_root" \
            '(Class\.forName|URLClassLoader|ScriptEngineManager)' \
            || violations=$((violations + 1))
    done

    if (( violations > 0 )); then
        echo "enterprise identity architecture violations: $violations" >&2
        return 1
    fi
}

self_test() {
    local fixture_root
    local violation_output
    fixture_root="$(mktemp -d -t skillhub-identity-architecture.XXXXXX)"

    cleanup_fixture() {
        if [[ -n "${fixture_root:-}" && -d "$fixture_root" && "$fixture_root" == /tmp/skillhub-identity-architecture.* ]]; then
            rm -rf -- "$fixture_root"
        fi
    }
    trap cleanup_fixture EXIT

    mkdir -p \
        "$fixture_root/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/organization" \
        "$fixture_root/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/identity/core" \
        "$fixture_root/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/federation/adapter"

    printf '%s\n' \
        'package com.iflytek.skillhub.domain.organization;' \
        'public record Organization(String id) {}' \
        > "$fixture_root/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/organization/Organization.java"
    printf '%s\n' \
        'package com.iflytek.skillhub.auth.identity.core;' \
        'public final class IdentityDecision {}' \
        > "$fixture_root/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/identity/core/IdentityDecision.java"

    if ! check_tree "$fixture_root" >/dev/null 2>&1; then
        echo "architecture self-test rejected the approved fixture" >&2
        return 1
    fi

    printf '%s\n' \
        'package com.iflytek.skillhub.domain.organization;' \
        'import com.iflytek.skillhub.auth.identity.IdentityBindingService;' \
        'public final class ForbiddenDomainDependency {}' \
        > "$fixture_root/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/organization/ForbiddenDomainDependency.java"
    printf '%s\n' \
        'package com.iflytek.skillhub.auth.identity.core;' \
        'public final class ProviderBranch { String provider = "github"; }' \
        > "$fixture_root/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/identity/core/ProviderBranch.java"
    printf '%s\n' \
        'package com.iflytek.skillhub.auth.federation.adapter;' \
        'import com.iflytek.skillhub.domain.user.UserAccountRepository;' \
        'public final class DirectStateWrite { void load() throws Exception { Class.forName("evil.Plugin"); } }' \
        > "$fixture_root/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/federation/adapter/DirectStateWrite.java"
    printf '%s\n' \
        'package com.iflytek.skillhub.domain.organization;' \
        'public final class ScimWirePayload { String protocol = "SCIM"; }' \
        > "$fixture_root/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/organization/ScimWirePayload.java"

    if violation_output="$(check_tree "$fixture_root" 2>&1)"; then
        echo "architecture self-test accepted intentional violations" >&2
        return 1
    fi

    for expected in \
        domain-organization-forbidden-dependency \
        domain-organization-protocol-leak \
        identity-core-provider-branch \
        adapter-direct-state-write \
        adapter-runtime-code-loading; do
        if [[ "$violation_output" != *"[$expected]"* ]]; then
            echo "architecture self-test did not detect $expected" >&2
            return 1
        fi
    done

    echo "enterprise identity architecture self-test passed"
}

require_rg
if [[ "${1:-}" == "--self-test" ]]; then
    self_test
    exit 0
fi
if [[ $# -ne 0 ]]; then
    echo "usage: $0 [--self-test]" >&2
    exit 2
fi

check_tree "$repository_root"
echo "enterprise identity architecture check passed"
