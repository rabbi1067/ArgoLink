package com.agrolink.app.service;

import com.agrolink.app.dto.DashboardSummaryDTO;
import com.agrolink.app.model.Role;

import java.time.LocalDate;

public interface DashboardService {


    DashboardSummaryDTO summary(String userId, Role role);


    DashboardSummaryDTO analytics(int months);

    DashboardSummaryDTO dashboard(String userId, Role role, LocalDate from, LocalDate to);
}
