package com.bezkoder.spring.jpa.postgresql.controller;

import com.bezkoder.spring.jpa.postgresql.service.OvertimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/overtime")
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    @GetMapping("/summary/{workerId}")
    public ResponseEntity<Map<String, Object>> getSummary(
            @PathVariable Long workerId,
            @RequestParam String month) {
        return ResponseEntity.ok(overtimeService.getMonthlySummary(workerId, month));
    }

    @PostMapping("/settle/{workerId}")
    public ResponseEntity<Map<String, Object>> settle(
            @PathVariable Long workerId,
            @RequestParam String month) {
        return ResponseEntity.ok(overtimeService.settleOvertime(workerId, month));
    }
}