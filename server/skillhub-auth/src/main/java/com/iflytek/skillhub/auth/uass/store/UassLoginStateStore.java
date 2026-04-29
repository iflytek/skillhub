package com.iflytek.skillhub.auth.uass.store;

import java.util.Optional;

public interface UassLoginStateStore {

    void save(String state, UassLoginState loginState);

    Optional<UassLoginState> find(String state);

    Optional<UassLoginState> consume(String state);

    void delete(String state);
}
