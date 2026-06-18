package com.ethan.chatapp.history.dto;

import lombok.Data;

@Data
public class ModelBreakdownDto {
    private String model;
    private long tokens;
    private double percentage;
}
