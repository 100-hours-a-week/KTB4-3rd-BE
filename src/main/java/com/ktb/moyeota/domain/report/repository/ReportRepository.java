package com.ktb.moyeota.domain.report.repository;

import com.ktb.moyeota.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
}
