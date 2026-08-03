package com.ilink.test.ilinkcheck.application.agent.feature.digest;

import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestSubscription;

import java.util.List;
import java.util.Optional;

public interface DigestSubscriptionRepository {

    DigestSubscription save(DigestSubscription subscription);

    Optional<DigestSubscription> findByUserId(String userId);

    List<DigestSubscription> findAll();

    void deleteByUserId(String userId);
}
