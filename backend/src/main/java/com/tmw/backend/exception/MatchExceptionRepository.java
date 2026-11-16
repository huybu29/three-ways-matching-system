package com.tmw.backend.exception;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchExceptionRepository extends JpaRepository<MatchException, Long> {

}