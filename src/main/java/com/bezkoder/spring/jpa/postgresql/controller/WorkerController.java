package com.bezkoder.spring.jpa.postgresql.controller;

import com.bezkoder.spring.jpa.postgresql.model.Worker;
import com.bezkoder.spring.jpa.postgresql.repository.WorkerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerRepository workerRepo;

    public WorkerController(WorkerRepository workerRepo) {
        this.workerRepo = workerRepo;
    }

    @PostMapping
    public ResponseEntity<Worker> create(@RequestBody Worker worker) {
        return ResponseEntity.ok(workerRepo.save(worker));
    }

    @GetMapping
    public ResponseEntity<List<Worker>> getAll() {
        return ResponseEntity.ok(workerRepo.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Worker> getById(@PathVariable Long id) {
        return ResponseEntity.ok(workerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Worker not found")));
    }
}