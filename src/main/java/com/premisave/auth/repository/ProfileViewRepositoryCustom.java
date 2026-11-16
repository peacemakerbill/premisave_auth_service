package com.premisave.auth.repository;

public interface ProfileViewRepositoryCustom {
    int countUniqueViewers(String targetId);

    /** Removes every view where the given user was the viewer or the profile that was viewed. */
    void deleteAllInvolving(String userId);
}