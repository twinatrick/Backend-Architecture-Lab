package com.example.BackendArchitectureLab.Vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheStatsEvent {
    private String cacheName;
    private String field;
}
