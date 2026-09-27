package com.agrolink.app.service;

import com.agrolink.app.dto.AnalyticsDTO;
import com.agrolink.app.dto.CropDistributionDTO;
import com.agrolink.app.dto.MonthlyVolumeDTO;
import com.agrolink.app.dto.RevenueDTO;
import com.agrolink.app.dto.SupplyDemandDTO;

import java.util.List;

public interface AnalyticsService {

    AnalyticsDTO overview();

    MonthlyVolumeDTO monthlyOrderVolume(int months);

    List<CropDistributionDTO> cropDistribution();

    RevenueDTO monthlyRevenue(int months);

    SupplyDemandDTO supplyDemand();
}