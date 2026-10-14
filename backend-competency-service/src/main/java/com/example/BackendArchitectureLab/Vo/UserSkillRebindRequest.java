package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "重新綁定使用者技能請求")
public class UserSkillRebindRequest {
    @ApiField(description = "使用者 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid", required = true)
    @NotBlank(message = "userId must not be blank")
    private String userId;

    @ApiField(description = "技能等級綁定清單", required = true)
    @NotEmpty(message = "bindings must not be empty")
    private @Valid List<@Valid SkillLevelBindingItem> bindings;
}
