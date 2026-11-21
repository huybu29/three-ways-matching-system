package com.tmw.backend.masterdata.service;

import java.util.List;

/** Nguồn cung cấp các chính sách đang hiệu lực (bản thật có cache, unit test dùng lambda). */
@FunctionalInterface
public interface ActivePolicyProvider {
    List<PolicySnapshot> activePolicies();
}
