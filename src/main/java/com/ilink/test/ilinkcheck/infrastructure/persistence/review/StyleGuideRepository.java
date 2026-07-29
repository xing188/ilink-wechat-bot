package com.ilink.test.ilinkcheck.infrastructure.persistence.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StyleGuideRepository extends JpaRepository<StyleGuideRule, String> {

    List<StyleGuideRule> findByKeywordContainingIgnoreCase(String keyword);
}
