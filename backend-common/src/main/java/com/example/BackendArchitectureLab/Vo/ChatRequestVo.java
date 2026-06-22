package com.example.BackendArchitectureLab.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestVo {
    private List<Map<String, String>> messages;
    private Double temperature;
    private Boolean stream = false;
}
