package com.agrolink.app.service;

import com.agrolink.app.dto.WeatherForecastDTO;

import java.util.List;

public interface WeatherService {


    WeatherForecastDTO getForecast(String district, int days);

    WeatherForecastDTO getForecastCached(String district, int days);

    WeatherForecastDTO refreshForecast(String district, int days);

    List<String> getDistricts();

    WeatherForecastDTO getForecastByLocation(double lat, double lon, int days);
}