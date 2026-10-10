package com.iflytek.skillhub.domain.suite;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records one successfully issued Suite plan without pre-counting member downloads. */
@Service
public class SkillSuiteInstallMetricsService {

    private final SkillSuiteRepository suiteRepository;

    public SkillSuiteInstallMetricsService(SkillSuiteRepository suiteRepository) {
        this.suiteRepository = suiteRepository;
    }

    @Transactional
    public void recordIssuedPlan(Long suiteId) {
        suiteRepository.incrementInstallRequestCount(suiteId);
    }
}
