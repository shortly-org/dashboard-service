package io.shortly.dashboard.dto;

import java.time.LocalDate;

public record DayPoint(LocalDate day, long clicks, long blocked) {}
