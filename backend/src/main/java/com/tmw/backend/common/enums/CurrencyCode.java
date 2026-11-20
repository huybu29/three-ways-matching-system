package com.tmw.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum CurrencyCode {

    VND("Việt Nam Đồng", 0),
    USD("Đô la Mỹ", 2),
    EUR("Euro", 2),
    JPY("Yên Nhật", 0),
    SGD("Đô la Singapore", 2),
    CNY("Nhân dân tệ", 2),
    KRW("Won Hàn Quốc", 0);

    private final String description;
    private final int standardScale;

    @JsonCreator
    public static CurrencyCode fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        for (CurrencyCode currency : values()) {
            if (currency.name().equals(normalized)) {
                return currency;
            }
        }
        String supported = Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                String.format("Mã tiền tệ '%s' không được hỗ trợ. Các mã hợp lệ: [%s]", rawCode, supported)
        );
    }
}