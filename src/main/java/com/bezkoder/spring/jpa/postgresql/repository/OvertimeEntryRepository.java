package com.bezkoder.spring.jpa.postgresql.repository;

import com.bezkoder.spring.jpa.postgresql.model.OvertimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface OvertimeEntryRepository extends JpaRepository<OvertimeEntry, Long> {

    // All overtime entries for a worker in a month
    @Query("SELECT o FROM OvertimeEntry o " +
            "WHERE o.worker.id = :workerId " +
            "AND FUNCTION('DATE_TRUNC', 'month', o.date) = " +
            "FUNCTION('DATE_TRUNC', 'month', CAST(:monthStart AS date))")
    List<OvertimeEntry> findByWorkerIdAndMonth(
            @Param("workerId") Long workerId,
            @Param("monthStart") LocalDate monthStart
    );

    // Total overtime hours for a worker in current month (for 60h cap check)
    @Query("SELECT COALESCE(SUM(o.overtimeHours), 0) FROM OvertimeEntry o " +
            "WHERE o.worker.id = :workerId " +
            "AND o.date >= :from AND o.date <= :to")
    BigDecimal sumOvertimeHoursByWorkerAndDateRange(
            @Param("workerId") Long workerId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}