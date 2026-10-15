package com.example.ecommerce.dashboard.service;

import com.example.ecommerce.dashboard.dto.DashboardResponse;

import java.time.LocalDate;

public interface DashboardService {
    DashboardResponse getDashboard(LocalDate date, String mode);
}
