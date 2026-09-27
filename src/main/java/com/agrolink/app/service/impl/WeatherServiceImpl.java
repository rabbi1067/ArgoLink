package com.agrolink.app.service.impl;

import com.agrolink.app.config.CacheConfig;
import com.agrolink.app.dto.DailyForecastDTO;
import com.agrolink.app.dto.WeatherForecastDTO;
import com.agrolink.app.exception.ResourceNotFoundException;
import com.agrolink.app.service.WeatherService;
import com.agrolink.app.util.BangladeshLocationRegions;
import com.agrolink.app.util.BangladeshLocationRegions.GeoPoint;
import com.agrolink.app.util.WeatherCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    private static final String OPEN_METEO = "https://api.open-meteo.com/v1/forecast";
    private static final String PROVIDER = "Open-Meteo (open-meteo.com · CC-BY-4.0)";
    private static final String UNITS = "°C · % · mm · km/h · hPa · WMO";
    private static final int MAX_DAYS = 7;
    private static final Duration REFRESH_COOLDOWN = Duration.ofSeconds(30);
    private static final String DISCLAIMER = "Source: " + PROVIDER + " — free forecast for planning hints only, "
            + "not official Bangladesh Meteorological Department guidance. Verify locally before "
            + "acting on any crop decision.";

    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    /** Last successful forecast per district — served (flagged stale) if a live call fails. */
    private final Map<String, WeatherForecastDTO> lastGood = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastRefresh = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ public API

    @Override
    public WeatherForecastDTO getForecast(String district, int days) {
        String name = resolve(district);
        return limit(load(name), days);
    }

    @Override
    public WeatherForecastDTO getForecastCached(String district, int days) {
        return getForecast(district, days);
    }

    @Override
    public WeatherForecastDTO refreshForecast(String district, int days) {
        String name = resolve(district);
        Instant now = Instant.now();
        Instant previous = lastRefresh.get(name);
        boolean allowed = previous == null || Duration.between(previous, now).compareTo(REFRESH_COOLDOWN) >= 0;
        if (allowed) {
            lastRefresh.put(name, now);
            Cache cache = cacheManager.getCache(CacheConfig.CACHE_WEATHER_DATA);
            if (cache != null) {
                cache.evict(name);
            }
        }
        return limit(load(name), days);
    }

    @Override
    public List<String> getDistricts() {
        return BangladeshLocationRegions.allDistricts();
    }

    @Override
    public WeatherForecastDTO getForecastByLocation(double lat, double lon, int days) {
        String nearest = BangladeshLocationRegions.nearestDistrict(lat, lon)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No known Bangladeshi district within 400 km of " + lat + ", " + lon + "."));
        return getForecast(nearest, days);
    }

    // ------------------------------------------------------------------ loading / caching

    private String resolve(String district) {
        return BangladeshLocationRegions.normalize(district)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown district '" + district + "'."));
    }

    private WeatherForecastDTO load(String name) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_WEATHER_DATA);
        // Per-district lock: concurrent requests for the same district trigger one upstream call.
        synchronized (locks.computeIfAbsent(name, k -> new Object())) {
            if (cache != null) {
                WeatherForecastDTO hit = cache.get(name, WeatherForecastDTO.class);
                if (hit != null) {
                    return hit;
                }
            }
            WeatherForecastDTO live = fetchLive(name);
            if (live != null) {
                if (cache != null) {
                    cache.put(name, live);
                }
                lastGood.put(name, live);
                return live;
            }
            WeatherForecastDTO old = lastGood.get(name);
            return old != null ? asStale(old) : unavailable(name);
        }
    }

    /** Returns a fresh forecast, or {@code null} if anything about the live call went wrong. */
    private WeatherForecastDTO fetchLive(String name) {
        GeoPoint p = BangladeshLocationRegions.coordinatesOf(name).orElseThrow();
        String division = BangladeshLocationRegions.divisionOf(name).orElse("Bangladesh");
        String url = OPEN_METEO + "?latitude=" + p.latitude() + "&longitude=" + p.longitude()
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,"
                + "wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation,rain,snowfall,"
                + "surface_pressure,cloud_cover,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,"
                + "rain_sum,precipitation_probability_max,precipitation_hours,snowfall_sum,wind_speed_10m_max,"
                + "wind_gusts_10m_max,wind_direction_10m_dominant,weather_code,uv_index_max,sunshine_duration,"
                + "shortwave_radiation_sum,sunrise,sunset"
                + "&timezone=Asia%2FDhaka&wind_speed_unit=kmh&forecast_days=" + MAX_DAYS;
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Open-Meteo returned HTTP {} for {}", resp.statusCode(), name);
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode daily = root.path("daily");
            JsonNode times = daily.path("time");
            if (!times.isArray() || times.size() == 0) {
                log.warn("Open-Meteo returned no daily data for {}", name);
                return null;
            }
            JsonNode tMax = daily.path("temperature_2m_max");
            JsonNode tMin = daily.path("temperature_2m_min");
            JsonNode appMax = daily.path("apparent_temperature_max");
            JsonNode appMin = daily.path("apparent_temperature_min");
            JsonNode pProb = daily.path("precipitation_probability_max");
            JsonNode rain = daily.path("rain_sum");
            JsonNode precipHours = daily.path("precipitation_hours");
            JsonNode snow = daily.path("snowfall_sum");
            JsonNode wind = daily.path("wind_speed_10m_max");
            JsonNode gust = daily.path("wind_gusts_10m_max");
            JsonNode windDir = daily.path("wind_direction_10m_dominant");
            JsonNode uv = daily.path("uv_index_max");
            JsonNode sunshine = daily.path("sunshine_duration");
            JsonNode radiation = daily.path("shortwave_radiation_sum");
            JsonNode sunrise = daily.path("sunrise");
            JsonNode sunset = daily.path("sunset");
            JsonNode wmo = daily.path("weather_code");

            boolean firstIsDay = true;
            List<DailyForecastDTO> rows = new ArrayList<>();
            for (int i = 0; i < times.size() && i < MAX_DAYS; i++) {
                Integer code = intAt(wmo, i);
                Double rainMm = numAt(rain, i);
                Double windKmh = numAt(wind, i);
                Double tempHigh = numAt(tMax, i);
                WeatherCodes.Advisory advisory =
                        WeatherCodes.advisory(rainMm, windKmh, tempHigh, code);
                rows.add(new DailyForecastDTO(
                        LocalDate.parse(times.get(i).asText()),
                        tempHigh,
                        numAt(tMin, i),
                        numAt(appMax, i),
                        numAt(appMin, i),
                        intAt(pProb, i),
                        rainMm,
                        numAt(precipHours, i),
                        windKmh,
                        numAt(gust, i),
                        intAt(windDir, i),
                        numAt(snow, i),
                        numAt(uv, i),
                        hoursFromSeconds(numAt(sunshine, i)),
                        numAt(radiation, i),
                        code,
                        WeatherCodes.describe(code),
                        WeatherCodes.icon(code, firstIsDay),
                        textAt(sunrise, i),
                        textAt(sunset, i),
                        advisory.label(),
                        advisory.level()));
                firstIsDay = false;
            }
            if (rows.stream().noneMatch(r -> r.tempMaxC() != null)) {
                log.warn("Open-Meteo payload for {} had no temperatures", name);
                return null;
            }
            return new WeatherForecastDTO(name, division, p.latitude(), p.longitude(), rows.size(), UNITS,
                    parseCurrent(root.path("current"), rows), List.copyOf(rows), Instant.now(), PROVIDER,
                    false, false, DISCLAIMER);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Weather fetch interrupted for {}", name);
            return null;
        } catch (Exception ex) {
            log.warn("Weather fetch failed for {}: {}", name, ex.toString());
            return null;
        }
    }

    private WeatherForecastDTO.Current parseCurrent(JsonNode cur, List<DailyForecastDTO> days) {
        Double temp = numNode(cur.path("temperature_2m"));
        if (temp == null) {
            return null;
        }
        Integer code = intNode(cur.path("weather_code"));
        JsonNode isDay = cur.path("is_day");
        Boolean day = (isDay.isMissingNode() || isDay.isNull()) ? null : isDay.asInt() == 1;
        DailyForecastDTO today = days.isEmpty() ? null : days.get(0);
        return new WeatherForecastDTO.Current(
                temp,
                numNode(cur.path("apparent_temperature")),
                numNode(cur.path("wind_speed_10m")),
                numNode(cur.path("wind_gusts_10m")),
                intNode(cur.path("wind_direction_10m")),
                numNode(cur.path("precipitation")),
                numNode(cur.path("rain")),
                numNode(cur.path("relative_humidity_2m")),
                numNode(cur.path("cloud_cover")),
                numNode(cur.path("surface_pressure")),
                numNode(cur.path("snowfall")),
                code,
                WeatherCodes.describe(code),
                WeatherCodes.icon(code, day == null || day),
                day,
                cur.path("time").asText(null),
                today == null ? null : today.sunrise(),
                today == null ? null : today.sunset());
    }

    // ------------------------------------------------------------------ fallbacks

    private WeatherForecastDTO asStale(WeatherForecastDTO o) {
        return new WeatherForecastDTO(o.district(), o.division(), o.latitude(), o.longitude(), o.days(), o.units(),
                o.current(), o.daily(), o.generatedAt(), o.provider(), false, true,
                "Live update failed, so this is the last successfully loaded forecast. " + DISCLAIMER);
    }

    private WeatherForecastDTO unavailable(String name) {
        GeoPoint p = BangladeshLocationRegions.coordinatesOf(name).orElseThrow();
        return new WeatherForecastDTO(name, BangladeshLocationRegions.divisionOf(name).orElse("Bangladesh"),
                p.latitude(), p.longitude(), 0, UNITS, null, List.of(), Instant.now(), PROVIDER, true, false,
                "Live forecast is temporarily unavailable. We never show made-up numbers — retry in a moment, "
                        + "or check the Bangladesh Meteorological Department / local agriculture office.");
    }

    private WeatherForecastDTO limit(WeatherForecastDTO full, int days) {
        int bounded = Math.min(Math.max(days, 1), MAX_DAYS);
        if (full.daily().size() <= bounded) {
            return full;
        }
        return new WeatherForecastDTO(full.district(), full.division(), full.latitude(), full.longitude(), bounded,
                full.units(), full.current(), List.copyOf(full.daily().subList(0, bounded)), full.generatedAt(),
                full.provider(), full.fallback(), full.stale(), full.disclaimer());
    }

    // ------------------------------------------------------------------ JSON helpers

    private static Double numNode(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull() || !n.isNumber()) {
            return null;
        }
        double v = n.asDouble();
        return Double.isNaN(v) ? null : v;
    }

    private static Integer intNode(JsonNode n) {
        return (n == null || n.isMissingNode() || n.isNull() || !n.isNumber()) ? null : n.asInt();
    }

    private static Double numAt(JsonNode arr, int i) {
        return numNode(arr.get(i));
    }

    private static Integer intAt(JsonNode arr, int i) {
        return intNode(arr.get(i));
    }

    private static String textAt(JsonNode arr, int i) {
        JsonNode node = arr == null ? null : arr.get(i);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    /** Open-Meteo returns sunshine/sunrise offsets in seconds; the UI wants hours. */
    private static Double hoursFromSeconds(Double seconds) {
        return seconds == null ? null : BigDecimal.valueOf(seconds / 3600.0)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}