package com.agrolink.app.controller.api;

import com.agrolink.app.dto.AnalyticsDTO;
import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.CropDistributionDTO;
import com.agrolink.app.dto.MonthlyVolumeDTO;
import com.agrolink.app.dto.RevenueDTO;
import com.agrolink.app.dto.SupplyDemandDTO;
import com.agrolink.app.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AnalyticsDTO>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.overview()));
    }

    @GetMapping("/monthly-volume")
    public ResponseEntity<ApiResponse<MonthlyVolumeDTO>> monthlyVolume(
            @RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.monthlyOrderVolume(months)));
    }

    @GetMapping("/crop-distribution")
    public ResponseEntity<ApiResponse<List<CropDistributionDTO>>> cropDistribution() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.cropDistribution()));
    }

    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<RevenueDTO>> revenue(@RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.monthlyRevenue(months)));
    }

    @GetMapping("/supply-demand")
    public ResponseEntity<ApiResponse<SupplyDemandDTO>> supplyDemand() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.supplyDemand()));
    }
}