package com.tmw.backend.identity.service;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.identity.api.IdentityQueryApi;
import com.tmw.backend.identity.domain.AppUser;
import com.tmw.backend.identity.domain.AppUserRepository;
import com.tmw.backend.identity.domain.UserStatus;
import com.tmw.backend.identity.seed.IdentityDataSeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityQueryService implements IdentityQueryApi {

    private final AppUserRepository appUserRepository;

    @Override
    public AppUser getUserReferenceById(UUID userId) {
        if (!appUserRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng với id: " + userId);
        }
        return appUserRepository.getReferenceById(userId);
    }

    @Override
    public AppUser getActiveUserById(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng với id: " + userId));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Tài khoản người dùng đã bị vô hiệu hóa");
        }
        return user;
    }

    @Override
    public AppUser getSystemUser() {
        return appUserRepository.findByUsername(IdentityDataSeeder.SYSTEM_USERNAME)
                .orElseThrow(() -> new IllegalStateException("Tài khoản hệ thống 'system' chưa được khởi tạo trong database"));
    }
}