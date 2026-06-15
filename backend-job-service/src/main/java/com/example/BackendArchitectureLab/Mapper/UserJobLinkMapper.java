package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Dto.Vo.UserJobLinkVo;
import com.example.BackendArchitectureLab.Entity.UserJobLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserJobLinkMapper {

    @Mapping(target = "id", expression = "java(link.getId() == null ? null : link.getId().toString())")
    @Mapping(target = "userId", expression = "java(link.getUser() == null || link.getUser().getId() == null ? null : link.getUser().getId().toString())")
    @Mapping(target = "userEmail", expression = "java(link.getUser() == null ? null : link.getUser().getEmail())")
    @Mapping(target = "jobPostingId", expression = "java(link.getJobPosting() == null || link.getJobPosting().getId() == null ? null : link.getJobPosting().getId().toString())")
    @Mapping(target = "jobTitle", expression = "java(link.getJobPosting() == null ? null : link.getJobPosting().getTitle())")
    @Mapping(target = "companyName", expression = "java(link.getJobPosting() == null || link.getJobPosting().getCompany() == null ? null : link.getJobPosting().getCompany().getName())")
    UserJobLinkVo toVo(UserJobLink link);

    @Mapping(target = "id", expression = "java(vo.getId() == null || vo.getId().isBlank() ? null : java.util.UUID.fromString(vo.getId()))")
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "jobPosting", ignore = true)
    UserJobLink toEntity(UserJobLinkVo vo);
}
