package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateUserJobLinkRequest {
    private String userId;
    private String jobPostingId;
    private String userNotes;
}
