package com.example.BackendArchitectureLab.Vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompensationRestoreResultVo {
    private boolean success;
    private String message;
    private UUID projectId;
    private UUID eventId;
}
