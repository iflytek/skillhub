package com.iflytek.skillhub.auth.uass;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Stores the normalized UASS session snapshot inside the local servlet session.
 */
@Service
public class UassSessionContextService {

    static final String SESSION_ATTRIBUTE = "uassSessionSnapshot";

    public void bind(UassLoginContext loginContext, HttpServletRequest request) {
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, UassSessionSnapshot.from(loginContext));
    }

    public Optional<UassLoginContext> load(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object attribute = session.getAttribute(SESSION_ATTRIBUTE);
        if (!(attribute instanceof UassSessionSnapshot snapshot)) {
            return Optional.empty();
        }
        return Optional.of(snapshot.toLoginContext());
    }

    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }
}
