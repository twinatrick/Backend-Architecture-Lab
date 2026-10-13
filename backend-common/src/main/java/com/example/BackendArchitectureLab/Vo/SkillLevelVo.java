package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "技能等級資料傳輸物件")
public class SkillLevelVo {
    @ApiField(description = "技能等級 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private String id;

    @ApiField(description = "所屬技能 UUID", example = "6ba7b810-9dad-11d1-80b4-00c04fd430c8", format = "uuid")
    private String skillId;

    @ApiField(description = "等級階層數值", example = "1")
    private Integer levelValue;

    @ApiField(description = "等級標題", example = "初階")
    private String title;

    @ApiField(description = "等級說明", example = "具備基礎操作與語法能力")
    private String description;

    @ApiField(description = "建立者 UUID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;

    @ApiField(description = "更新者 UUID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String updatedBy;

    @ApiField(description = "建立時間戳記")
    private Date createdTime;

    @ApiField(description = "更新時間戳記")
    private Date updatedTime;
}
