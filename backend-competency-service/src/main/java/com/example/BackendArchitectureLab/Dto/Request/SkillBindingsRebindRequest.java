package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "重新綁定技能項目清單請求")
public class SkillBindingsRebindRequest {
    @ApiField(description = "技能等級綁定清單", required = true)
    @NotEmpty(message = "bindings must not be empty")
    private @Valid List<@Valid SkillLevelBindingItem> bindings;
}
