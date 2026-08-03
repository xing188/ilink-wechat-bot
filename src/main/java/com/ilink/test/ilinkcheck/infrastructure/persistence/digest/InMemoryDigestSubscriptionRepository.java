package com.ilink.test.ilinkcheck.infrastructure.persistence.digest;

import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestSubscription;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DigestSubscriptionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 晨报订阅内存仓储。
 * 当前用于演示和联调；应用重启后订阅不会保留。
 */
@Repository
public class InMemoryDigestSubscriptionRepository implements DigestSubscriptionRepository {

    private final ConcurrentMap<String, DigestSubscription> subscriptions = new ConcurrentHashMap<>();

    @Override
    public DigestSubscription save(DigestSubscription subscription) {
        subscriptions.put(subscription.userId(), subscription);
        return subscription;
    }

    @Override
    public Optional<DigestSubscription> findByUserId(String userId) {
        return Optional.ofNullable(subscriptions.get(userId));
    }

    @Override
    public List<DigestSubscription> findAll() {
        return List.copyOf(subscriptions.values());
    }

    @Override
    public void deleteByUserId(String userId) {
        subscriptions.remove(userId);
    }
}
