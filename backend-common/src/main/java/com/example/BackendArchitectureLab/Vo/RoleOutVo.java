package com.example.BackendArchitectureLab.Vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
public class RoleOutVo {
    private UUID id;
    private String name;
    private String description;
    @Schema(description = "建立者 UUID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
    @Schema(description = "最後更新者 UUID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String updatedBy;
    private Date createdTime;
    private Date updatedTime;
    private Integer functionCount;
    private List<String> functionIds;
}
