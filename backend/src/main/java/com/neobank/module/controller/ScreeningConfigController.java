package com.neobank.module.controller;

import com.neobank.module.dto.ScreeningConfigCreateRequest;
import com.neobank.module.dto.ScreeningConfigDetailView;
import com.neobank.module.dto.ScreeningConfigSummaryView;
import com.neobank.module.service.ScreeningConfigService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/screening-configs")
public class ScreeningConfigController {

    private final ScreeningConfigService configs;

    public ScreeningConfigController(ScreeningConfigService configs) {
        this.configs = configs;
    }

    @GetMapping
    public List<ScreeningConfigSummaryView> list() {
        return configs.list();
    }

    @GetMapping("/current")
    public ScreeningConfigDetailView current() {
        return configs.getCurrent();
    }

    @GetMapping("/{version}")
    public ScreeningConfigDetailView get(@PathVariable Integer version) {
        return configs.get(version);
    }

    @PostMapping
    public ResponseEntity<ScreeningConfigDetailView> create(
            @Valid @RequestBody ScreeningConfigCreateRequest request) {
        ScreeningConfigDetailView created = configs.create(request);
        URI location = URI.create("/api/v1/screening-configs/" + created.version());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{version}/activate")
    public ScreeningConfigDetailView activate(@PathVariable Integer version) {
        return configs.activate(version);
    }

    @DeleteMapping("/{version}")
    public ResponseEntity<Void> delete(@PathVariable Integer version) {
        configs.delete(version);
        return ResponseEntity.noContent().build();
    }
}