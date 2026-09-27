package com.ktb.moyeota.domain.report.repository;

import com.ktb.moyeota.domain.report.entity.MessageReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReportRepository extends JpaRepository<MessageReport, Long> {
}
