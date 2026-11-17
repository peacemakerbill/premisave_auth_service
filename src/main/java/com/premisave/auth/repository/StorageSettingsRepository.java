package com.premisave.auth.repository;

import com.premisave.auth.entity.StorageSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StorageSettingsRepository extends MongoRepository<StorageSettings, String> {
}
