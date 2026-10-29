package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "技能資料傳輸模型")
public class SkillVo {
    @ApiField(description = "技能唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private UUID id;
    @ApiField(description = "技能名稱", example = "Spring Boot")
    private String name;

    @ApiField(description = "技能說明描述", example = "Java 企業級微服務開發框架")
    private String description;
    @ApiField(description = "建立者 UUID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
    @ApiField(description = "最後更新者 UUID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String updatedBy;
    @ApiField(description = "建立時間", example = "2026-04-12T08:00:00Z")
    private Date createdTime;
    @ApiField(description = "最後更新時間", example = "2026-04-12T09:00:00Z")
    private Date updatedTime;
    
    /**
     * 管理者模式使用：要綁定的使用者 ID 陣列
     */
    @ApiField(description = "綁定之使用者 UUID 清單")
    private List<String> userIds;
    
    /**
     * 管理者模式使用：預設技能等級 ID
     */
    @ApiField(description = "預設技能等級 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private String skillLevelId;
}
