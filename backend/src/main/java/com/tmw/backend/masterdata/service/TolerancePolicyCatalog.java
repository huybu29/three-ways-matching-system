package com.tmw.backend.masterdata.service;

import com.tmw.backend.masterdata.config.PolicyCacheConfig;
import com.tmw.backend.masterdata.repository.TolerancePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Đọc toàn bộ chính sách đang hiệu lực và cache lại. Cache bị xóa khi tạo/sửa chính sách. */
@Component
@RequiredArgsConstructor
public class TolerancePolicyCatalog implements ActivePolicyProvider {

    private final TolerancePolicyRepository repository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(PolicyCacheConfig.TOLERANCE_POLICIES)
    public List<PolicySnapshot> activePolicies() {
        return repository.findByActiveTrue().stream().map(PolicySnapshot::from).toList();
    }
}
