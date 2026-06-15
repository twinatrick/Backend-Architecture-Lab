package com.example.BackendArchitectureLab.Dto.Vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UserRoleRebindRequest {
    private String userId;

    @NotEmpty(message = "roleIds must not be empty")
    private List<String> roleIds;
}
