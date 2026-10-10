package com.iflytek.skillhub.domain.skill.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensation;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationRepository;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationStatus;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillStorageDeletionCompensationService {

    private static final TypeReference<List<String>> STORAGE_KEYS_TYPE = new TypeReference<>() {};

    private final SkillStorageDeletionCompensationRepository repository;
    private final ObjectStorageService objectStorageService;
    private final ObjectMapper objectMapper;

    public SkillStorageDeletionCompensationService(SkillStorageDeletionCompensationRepository repository,
                                                   ObjectStorageService objectStorageService,
                                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectStorageService = objectStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordFailure(Long skillId,
                              String namespace,
                              String slug,
                              List<String> storageKeys,
                              String lastError) {
        repository.save(new SkillStorageDeletionCompensation(
                skillId,
                namespace,
                slug,
                serialize(storageKeys),
                lastError
        ));
    }

    @Transactional
    public int retryPendingCleanup() {
        List<SkillStorageDeletionCompensation> records =
                repository.findTop100ByStatusOrderByCreatedAtAsc(SkillStorageDeletionCompensationStatus.PENDING);
        for (SkillStorageDeletionCompensation record : records) {
            try {
                objectStorageService.deleteObjects(deserialize(record.getStorageKeysJson()));
                record.markCompleted();
            } catch (RuntimeException ex) {
                record.markAttempt(ex.getMessage());
            }
        }
        return records.size();
    }

    private String serialize(List<String> storageKeys) {
        try {
            return objectMapper.writeValueAsString(storageKeys);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize storage deletion compensation keys", e);
        }
    }

    private List<String> deserialize(String storageKeysJson) {
        try {
            return objectMapper.readValue(storageKeysJson, STORAGE_KEYS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize storage deletion compensation keys", e);
        }
    }
}
