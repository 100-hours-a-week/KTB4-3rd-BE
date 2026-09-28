package com.ktb.moyeota.domain.report.repository;

import com.ktb.moyeota.domain.report.entity.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {
}
