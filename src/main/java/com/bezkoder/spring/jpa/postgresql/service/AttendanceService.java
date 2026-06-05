package com.bezkoder.spring.jpa.postgresql.service;

import com.bezkoder.spring.jpa.postgresql.exception.BusinessException;
import com.bezkoder.spring.jpa.postgresql.exception.ResourceNotFoundException;
import com.bezkoder.spring.jpa.postgresql.model.AttendanceLog;
import com.bezkoder.spring.jpa.postgresql.model.OvertimeEntry;
import com.bezkoder.spring.jpa.postgresql.model.Site;
import com.bezkoder.spring.jpa.postgresql.model.Worker;
import com.bezkoder.spring.jpa.postgresql.repository.AttendanceLogRepository;
import com.bezkoder.spring.jpa.postgresql.repository.OvertimeEntryRepository;
import com.bezkoder.spring.jpa.postgresql.repository.SiteRepository;
import com.bezkoder.spring.jpa.postgresql.repository.WorkerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private static final String ACTIVE_WORKERS_KEY = "active_workers:";
    private static final int STANDARD_HOURS = 8;
    private static final int MAX_SHIFT_HOURS = 16;
    private static final int MONTHLY_OT_CAP = 60;

    private final AttendanceLogRepository attendanceRepo;
    private final OvertimeEntryRepository overtimeRepo;
    private final WorkerRepository workerRepo;
    private final SiteRepository siteRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    public AttendanceService(AttendanceLogRepository attendanceRepo,
                             OvertimeEntryRepository overtimeRepo,
                             WorkerRepository workerRepo,
                             SiteRepository siteRepo,
                             RedisTemplate<String, Object> redisTemplate) {
        this.attendanceRepo = attendanceRepo;
        this.overtimeRepo = overtimeRepo;
        this.workerRepo = workerRepo;
        this.siteRepo = siteRepo;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AttendanceLog clockIn(Long workerId, Long siteId) {
        // Validate worker
        Worker worker = workerRepo.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found with id: " + workerId));
        if (!worker.isActive()) {
            throw new BusinessException("WORKER_INACTIVE", "Worker is not active");
        }

        // Check double clock-in
        if (attendanceRepo.existsByWorkerAndClockOutIsNull(worker)) {
            throw new BusinessException("DUPLICATE_CLOCK_IN",
                    "Worker is already clocked in");
        }

        // Validate site
        Site site = siteRepo.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site not found with id: " + siteId));
        if (!site.isActive()) {
            throw new BusinessException("SITE_INACTIVE", "Site is not active");
        }

        // Create attendance record
        AttendanceLog log = new AttendanceLog();
        log.setWorker(worker);
        log.setSite(site);
        log.setClockIn(LocalDateTime.now());
        AttendanceLog saved = attendanceRepo.save(log);

        // Add to Redis cache
        try {
            String key = ACTIVE_WORKERS_KEY + workerId;
            Map<String, Object> cacheData = new HashMap<>();
            cacheData.put("workerId", workerId);
            cacheData.put("workerName", worker.getName());
            cacheData.put("siteId", siteId);
            cacheData.put("siteName", site.getSiteName());
            cacheData.put("clockIn", LocalDateTime.now().toString());
            cacheData.put("attendanceId", saved.getId());
            redisTemplate.opsForValue().set(key, cacheData, MAX_SHIFT_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("Redis error during clock-in: " + e.getMessage());
        }

        return saved;
    }

    @Transactional
    public AttendanceLog clockOut(Long workerId) {
        Worker worker = workerRepo.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found with id: " + workerId));

        AttendanceLog log = attendanceRepo.findByWorkerAndClockOutIsNull(worker)
                .orElseThrow(() -> new BusinessException("NOT_CLOCKED_IN",
                        "Worker is not currently clocked in"));

        LocalDateTime clockOut = LocalDateTime.now();
        log.setClockOut(clockOut);

        // Calculate total hours
        double totalHours = Duration.between(log.getClockIn(), clockOut).toMinutes() / 60.0;
        log.setTotalHours(BigDecimal.valueOf(totalHours).setScale(2, RoundingMode.HALF_UP));

        // Flag if shift exceeds 16 hours
        if (totalHours > MAX_SHIFT_HOURS) {
            log.setFlagged(true);
        }

        // Calculate overtime
        if (totalHours > STANDARD_HOURS) {
            double overtimeHours = totalHours - STANDARD_HOURS;

            // Check monthly cap
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            LocalDate monthEnd = YearMonth.now().atEndOfMonth();
            BigDecimal usedOT = overtimeRepo.sumOvertimeHoursByWorkerAndDateRange(
                    workerId, monthStart, monthEnd);
            double usedOTDouble = usedOT.doubleValue();
            double remainingCap = MONTHLY_OT_CAP - usedOTDouble;

            if (remainingCap > 0) {
                double cappedOT = Math.min(overtimeHours, remainingCap);
                log.setOvertimeHours(BigDecimal.valueOf(cappedOT).setScale(2, RoundingMode.HALF_UP));

                // Calculate OT amount: 1.5x for first 2 hours, 2x beyond
                BigDecimal hourlyRate = worker.getDailyWageRate()
                        .divide(BigDecimal.valueOf(STANDARD_HOURS), 4, RoundingMode.HALF_UP);
                BigDecimal otAmount;
                if (cappedOT <= 2) {
                    otAmount = hourlyRate.multiply(BigDecimal.valueOf(1.5))
                            .multiply(BigDecimal.valueOf(cappedOT));
                } else {
                    BigDecimal first2 = hourlyRate.multiply(BigDecimal.valueOf(1.5))
                            .multiply(BigDecimal.valueOf(2));
                    BigDecimal beyond2 = hourlyRate.multiply(BigDecimal.valueOf(2.0))
                            .multiply(BigDecimal.valueOf(cappedOT - 2));
                    otAmount = first2.add(beyond2);
                }

                // Save overtime entry
                OvertimeEntry ot = new OvertimeEntry();
                ot.setWorker(worker);
                ot.setAttendance(log);
                ot.setDate(today);
                ot.setOvertimeHours(BigDecimal.valueOf(cappedOT).setScale(2, RoundingMode.HALF_UP));
                ot.setOvertimeRate(hourlyRate);
                ot.setAmount(otAmount.setScale(2, RoundingMode.HALF_UP));
                overtimeRepo.save(ot);
            }
        }

        AttendanceLog saved = attendanceRepo.save(log);

        // Remove from Redis
        try {
            redisTemplate.delete(ACTIVE_WORKERS_KEY + workerId);
        } catch (Exception e) {
            System.err.println("Redis error during clock-out: " + e.getMessage());
        }

        return saved;
    }

    public List<Map<String, Object>> getActiveWorkers() {
        try {
            Set<String> keys = redisTemplate.keys(ACTIVE_WORKERS_KEY + "*");
            if (keys == null || keys.isEmpty()) return List.of();
            return keys.stream()
                    .map(key -> (Map<String, Object>) redisTemplate.opsForValue().get(key))
                    .filter(val -> val != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Redis error fetching active workers: " + e.getMessage());
            return List.of();
        }
    }

    public Page<AttendanceLog> getAttendanceLog(Long workerId, LocalDateTime from,
                                                LocalDateTime to, Pageable pageable) {
        workerRepo.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found with id: " + workerId));
        return attendanceRepo.findByWorkerIdAndDateRange(workerId, from, to, pageable);
    }
}