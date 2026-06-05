package com.bezkoder.spring.jpa.postgresql.controller;

import com.bezkoder.spring.jpa.postgresql.model.Site;
import com.bezkoder.spring.jpa.postgresql.repository.SiteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sites")
public class SiteController {

    private final SiteRepository siteRepo;

    public SiteController(SiteRepository siteRepo) {
        this.siteRepo = siteRepo;
    }

    @PostMapping
    public ResponseEntity<Site> create(@RequestBody Site site) {
        return ResponseEntity.ok(siteRepo.save(site));
    }

    @GetMapping
    public ResponseEntity<List<Site>> getAll() {
        return ResponseEntity.ok(siteRepo.findAll());
    }
}