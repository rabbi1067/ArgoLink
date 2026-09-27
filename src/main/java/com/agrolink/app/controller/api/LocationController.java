package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.util.BangladeshLocationRegions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    public record DistrictOption(String name, String division) {
    }

    @GetMapping("/divisions")
    public ResponseEntity<ApiResponse<List<String>>> divisions() {
        return ResponseEntity.ok(ApiResponse.ok(BangladeshLocationRegions.DIVISIONS));
    }

    @GetMapping("/districts")
    public ResponseEntity<ApiResponse<List<DistrictOption>>> districts(
            @RequestParam(required = false) String division) {
        String wanted = division == null ? "" : division.trim();
        List<DistrictOption> districts = BangladeshLocationRegions.allDistricts().stream()
                .map(name -> new DistrictOption(name, BangladeshLocationRegions.divisionOf(name).orElse("")))
                .filter(option -> wanted.isEmpty() || option.division().equalsIgnoreCase(wanted))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(districts));
    }
}