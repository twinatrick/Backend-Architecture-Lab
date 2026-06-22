package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Vo.UserJobLinkVo;
import com.example.BackendArchitectureLab.Entity.UserJobLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserJobLinkMapper {

    @Mapping(target = "id", expression = "java(link.getId() == null ? null : link.getId().toString())")
    @Mapping(target = "userId", expression = "java(link.getUserId() == null ? null : link.getUserId().toString())")
    @Mapping(target = "userEmail", ignore = true)
    @Mapping(target = "jobPostingId", expression = "java(link.getJobPosting() == null || link.getJobPosting().getId() == null ? null : link.getJobPosting().getId().toString())")
    @Mapping(target = "jobTitle", expression = "java(link.getJobPosting() == null ? null : link.getJobPosting().getTitle())")
    @Mapping(target = "companyName", expression = "java(link.getJobPosting() == null || link.getJobPosting().getCompany() == null ? null : link.getJobPosting().getCompany().getName())")
    UserJobLinkVo toVo(UserJobLink link);

    @Mapping(target = "id", expression = "java(vo.getId() == null || vo.getId().isBlank() ? null : java.util.UUID.fromString(vo.getId()))")
    @Mapping(target = "userId", expression = "java(vo.getUserId() == null || vo.getUserId().isBlank() ? null : java.util.UUID.fromString(vo.getUserId()))")
    @Mapping(target = "jobPosting", ignore = true)
    UserJobLink toEntity(UserJobLinkVo vo);
}
