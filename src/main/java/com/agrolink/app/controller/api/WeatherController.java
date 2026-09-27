package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.WeatherForecastDTO;
import com.agrolink.app.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/districts")
    public ResponseEntity<ApiResponse<List<String>>> districts() {
        return ResponseEntity.ok(ApiResponse.ok("Districts loaded", weatherService.getDistricts()));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<WeatherForecastDTO>> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(value = "days", required = false, defaultValue = "7") int days) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Location forecast retrieved", weatherService.getForecastByLocation(lat, lon, days)));
    }


    @GetMapping("/forecast")
    public ResponseEntity<ApiResponse<WeatherForecastDTO>> forecast(
            @RequestParam(value = "district", required = false, defaultValue = "Dhaka") String district,
            @RequestParam(value = "days", required = false, defaultValue = "7") int days,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        WeatherForecastDTO forecast = refresh
                ? weatherService.refreshForecast(district, days)
                : weatherService.getForecastCached(district, days);
        return ResponseEntity.ok(ApiResponse.ok("Forecast retrieved", forecast));
    }
}