package com.premisave.auth.repository;

import com.premisave.auth.entity.HomeOwnerApplication;
import com.premisave.auth.enums.ApplicationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HomeOwnerApplicationRepository extends MongoRepository<HomeOwnerApplication, String> {

    List<HomeOwnerApplication> findByApplicantIdOrderByCreatedAtDesc(String applicantId);

    Optional<HomeOwnerApplication> findFirstByApplicantIdAndStatusIn(String applicantId, Collection<ApplicationStatus> statuses);

    long countByStatus(ApplicationStatus status);

    Optional<HomeOwnerApplication> findFirstByStatusInOrderBySubmittedAtAsc(Collection<ApplicationStatus> statuses);

    List<HomeOwnerApplication> findTop200ByDecidedAtNotNullOrderByDecidedAtDesc();
}