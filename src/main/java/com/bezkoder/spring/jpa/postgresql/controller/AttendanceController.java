package com.bezkoder.spring.jpa.postgresql.controller;

import com.bezkoder.spring.jpa.postgresql.model.AttendanceLog;
import com.bezkoder.spring.jpa.postgresql.service.AttendanceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/clock-in")
    public ResponseEntity<AttendanceLog> clockIn(@RequestBody Map<String, Long> request) {
        Long workerId = request.get("workerId");
        Long siteId = request.get("siteId");
        return ResponseEntity.ok(attendanceService.clockIn(workerId, siteId));
    }

    @PostMapping("/clock-out")
    public ResponseEntity<AttendanceLog> clockOut(@RequestBody Map<String, Long> request) {
        Long workerId = request.get("workerId");
        return ResponseEntity.ok(attendanceService.clockOut(workerId));
    }

    @GetMapping("/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveWorkers() {
        return ResponseEntity.ok(attendanceService.getActiveWorkers());
    }

    @GetMapping("/log")
    public ResponseEntity<Page<AttendanceLog>> getLog(
            @RequestParam Long workerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(attendanceService.getAttendanceLog(workerId, from, to, pageable));
    }
}