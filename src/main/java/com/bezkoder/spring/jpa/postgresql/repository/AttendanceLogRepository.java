package com.bezkoder.spring.jpa.postgresql.repository;

import com.bezkoder.spring.jpa.postgresql.model.AttendanceLog;
import com.bezkoder.spring.jpa.postgresql.model.Worker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    // Find open attendance (clocked in, not yet clocked out)
    Optional<AttendanceLog> findByWorkerAndClockOutIsNull(Worker worker);

    // Check if worker is currently clocked in
    boolean existsByWorkerAndClockOutIsNull(Worker worker);

    // Paginated attendance history with JOIN FETCH to avoid N+1
    @Query("SELECT a FROM AttendanceLog a " +
            "JOIN FETCH a.worker w " +
            "JOIN FETCH a.site s " +
            "WHERE a.worker.id = :workerId " +
            "AND a.clockIn >= :from " +
            "AND a.clockIn <= :to")
    Page<AttendanceLog> findByWorkerIdAndDateRange(
            @Param("workerId") Long workerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}