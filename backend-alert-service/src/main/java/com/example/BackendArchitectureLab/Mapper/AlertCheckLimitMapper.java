package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Dto.Vo.AlertCheckLimitVo;
import com.example.BackendArchitectureLab.Entity.AlertCheckLimit;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AlertCheckLimitMapper {
    AlertCheckLimitVo toVo(AlertCheckLimit alertCheckLimit);

    AlertCheckLimit toEntity(AlertCheckLimitVo alertCheckLimitVo);
}
