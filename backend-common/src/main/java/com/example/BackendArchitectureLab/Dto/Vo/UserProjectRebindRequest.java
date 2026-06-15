package com.example.BackendArchitectureLab.Dto.Vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UserProjectRebindRequest {
    @NotBlank(message = "userId must not be blank")
    private String userId;

    @NotEmpty(message = "projectIds must not be empty")
    private List<@NotBlank String> projectIds;
}
