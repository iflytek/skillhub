package com.iflytek.skillhub.infra.identity;

import com.iflytek.skillhub.domain.organization.OrganizationDomainProofLookupException;
import com.iflytek.skillhub.domain.organization.OrganizationDomainProofResolver;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NameNotFoundException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Component;

/** Bounded JNDI DNS adapter used only from the application's non-transactional verification step. */
@Component
public class JndiOrganizationDomainProofResolver
        implements OrganizationDomainProofResolver {

    private static final String TXT_ATTRIBUTE = "TXT";
    private static final String INITIAL_TIMEOUT_MILLIS = "2000";
    private static final String RETRIES = "1";

    private final TxtLookup lookup;

    public JndiOrganizationDomainProofResolver() {
        this(JndiOrganizationDomainProofResolver::lookupWithJndi);
    }

    JndiOrganizationDomainProofResolver(TxtLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public List<String> resolveTxt(String recordName) {
        try {
            Attributes attributes = lookup.lookup(recordName);
            Attribute txt = attributes == null ? null : attributes.get(TXT_ATTRIBUTE);
            if (txt == null) {
                return List.of();
            }
            List<String> records = new ArrayList<>();
            NamingEnumeration<?> values = txt.getAll();
            try {
                while (values.hasMore()) {
                    String normalized = normalizeTxtValue(values.next());
                    if (!normalized.isBlank()) {
                        records.add(normalized);
                    }
                }
            } finally {
                values.close();
            }
            return List.copyOf(records);
        } catch (NameNotFoundException exception) {
            return List.of();
        } catch (NamingException exception) {
            throw new OrganizationDomainProofLookupException();
        }
    }

    private static Attributes lookupWithJndi(String recordName) throws NamingException {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", INITIAL_TIMEOUT_MILLIS);
        environment.put("com.sun.jndi.dns.timeout.retries", RETRIES);
        InitialDirContext context = new InitialDirContext(environment);
        try {
            return context.getAttributes(recordName, new String[]{TXT_ATTRIBUTE});
        } finally {
            context.close();
        }
    }

    static String normalizeTxtValue(Object value) {
        String text = String.valueOf(value).trim();
        if (!text.contains("\"")) {
            return text;
        }
        StringBuilder normalized = new StringBuilder(text.length());
        boolean insideQuotedSegment = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\"') {
                insideQuotedSegment = !insideQuotedSegment;
            } else if (insideQuotedSegment || !Character.isWhitespace(current)) {
                normalized.append(current);
            }
        }
        return normalized.toString();
    }

    @FunctionalInterface
    interface TxtLookup {
        Attributes lookup(String recordName) throws NamingException;
    }
}
