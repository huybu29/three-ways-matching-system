package com.tmw.backend.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@MappedSuperclass
public abstract class VersionedEntity extends BaseEntity {

    @Version
    @Setter(AccessLevel.NONE)
    @Column(name = "version", nullable = false)
    private Integer version;

    public String getETag() {
        return version != null ? "\"" + version + "\"" : null;
    }
}