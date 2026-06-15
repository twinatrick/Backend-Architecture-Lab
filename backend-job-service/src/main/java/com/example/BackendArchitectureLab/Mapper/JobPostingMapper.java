package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Dto.Vo.JobPostingVo;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface JobPostingMapper {

    @Mapping(target = "id", expression = "java(jobPosting.getId() == null ? null : jobPosting.getId().toString())")
    @Mapping(target = "companyId", expression = "java(jobPosting.getCompany() == null || jobPosting.getCompany().getId() == null ? null : jobPosting.getCompany().getId().toString())")
    @Mapping(target = "companyName", expression = "java(jobPosting.getCompany() == null ? null : jobPosting.getCompany().getName())")
    JobPostingVo toVo(JobPosting jobPosting);

    @Mapping(target = "id", expression = "java(jobPostingVo.getId() == null || jobPostingVo.getId().isBlank() ? null : java.util.UUID.fromString(jobPostingVo.getId()))")
    @Mapping(target = "company", ignore = true)
    JobPosting toEntity(JobPostingVo jobPostingVo);
}
