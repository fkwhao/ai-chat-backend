package com.ethan.chatapp.history.dto;

import lombok.Data;

@Data
public class DailyTrendDto {
    private String date;
    private String model;
    private long tokens;
}
