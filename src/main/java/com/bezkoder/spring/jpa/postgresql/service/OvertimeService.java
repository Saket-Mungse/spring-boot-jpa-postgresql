package com.bezkoder.spring.jpa.postgresql.service;

import com.bezkoder.spring.jpa.postgresql.event.OvertimeSettledEvent;
import com.bezkoder.spring.jpa.postgresql.exception.BusinessException;
import com.bezkoder.spring.jpa.postgresql.exception.ResourceNotFoundException;
import com.bezkoder.spring.jpa.postgresql.model.OvertimeEntry;
import com.bezkoder.spring.jpa.postgresql.repository.OvertimeEntryRepository;
import com.bezkoder.spring.jpa.postgresql.repository.WorkerRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OvertimeService {

    private final OvertimeEntryRepository overtimeRepo;
    private final WorkerRepository workerRepo;
    private final ApplicationEventPublisher eventPublisher;

    public OvertimeService(OvertimeEntryRepository overtimeRepo,
                           WorkerRepository workerRepo,
                           ApplicationEventPublisher eventPublisher) {
        this.overtimeRepo = overtimeRepo;
        this.workerRepo = workerRepo;
        this.eventPublisher = eventPublisher;
    }

    public Map<String, Object> getMonthlySummary(Long workerId, String month) {
        workerRepo.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Worker not found with id: " + workerId));

        YearMonth yearMonth = YearMonth.parse(month);
        LocalDate monthStart = yearMonth.atDay(1);
        List<OvertimeEntry> entries = overtimeRepo
                .findByWorkerIdAndMonth(workerId, monthStart);

        BigDecimal totalHours = entries.stream()
                .map(OvertimeEntry::getOvertimeHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmount = entries.stream()
                .map(OvertimeEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> breakdown = entries.stream().map(e -> {
            Map<String, Object> item = new HashMap<>();
            item.put("date", e.getDate().toString());
            item.put("overtimeHours", e.getOvertimeHours());
            item.put("amount", e.getAmount());
            item.put("status", e.getSettlementStatus().toString());
            return item;
        }).toList();

        Map<String, Object> summary = new HashMap<>();
        summary.put("workerId", workerId);
        summary.put("month", month);
        summary.put("totalOvertimeHours", totalHours);
        summary.put("totalPayoutAmount", totalAmount);
        summary.put("breakdown", breakdown);
        return summary;
    }

    @Transactional
    public Map<String, Object> settleOvertime(Long workerId, String month) {
        workerRepo.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Worker not found with id: " + workerId));

        // Cannot settle current or future month
        YearMonth requestedMonth = YearMonth.parse(month);
        YearMonth currentMonth = YearMonth.now();
        if (!requestedMonth.isBefore(currentMonth)) {
            throw new BusinessException("INVALID_SETTLEMENT_MONTH",
                    "Cannot settle current or future month. Only past months allowed.");
        }

        LocalDate monthStart = requestedMonth.atDay(1);
        List<OvertimeEntry> entries = overtimeRepo
                .findByWorkerIdAndMonth(workerId, monthStart);

        if (entries.isEmpty()) {
            throw new BusinessException("NO_OVERTIME_ENTRIES",
                    "No overtime entries found for worker in " + month);
        }

        boolean allSettled = entries.stream()
                .allMatch(e -> e.getSettlementStatus() ==
                        OvertimeEntry.SettlementStatus.SETTLED);
        if (allSettled) {
            throw new BusinessException("ALREADY_SETTLED",
                    "Overtime for " + month + " is already settled");
        }

        // Settle all atomically — entire batch or nothing
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OvertimeEntry entry : entries) {
            if (entry.getSettlementStatus() == OvertimeEntry.SettlementStatus.PENDING) {
                entry.setSettlementStatus(OvertimeEntry.SettlementStatus.SETTLED);
                totalAmount = totalAmount.add(entry.getAmount());
            }
        }
        overtimeRepo.saveAll(entries);

        // Publish event — SMS fires AFTER_COMMIT, not inside this transaction
        eventPublisher.publishEvent(
                new OvertimeSettledEvent(this, workerId, month, totalAmount, entries.size())
        );

        Map<String, Object> result = new HashMap<>();
        result.put("workerId", workerId);
        result.put("month", month);
        result.put("settledAmount", totalAmount);
        result.put("entriesSettled", entries.size());
        result.put("status", "SETTLED");
        return result;
    }


    // Fetch external data BEFORE transaction opens
    // This prevents holding a DB connection while waiting on external API
    private BigDecimal fetchMinimumWageRate() {
        try {
            // In production: RestTemplate/WebClient call to govt API
            // Fetched OUTSIDE @Transactional so DB connection is not held
            return new BigDecimal("400.00"); // fallback default
        } catch (Exception e) {
            System.err.println("External wage API unavailable, using default: " + e.getMessage());
            return new BigDecimal("400.00");
        }
    }
}