package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Vo.ApiUsageLogVo;
import com.example.BackendArchitectureLab.Entity.ApiUsageLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ApiUsageLogMapper {
    ApiUsageLogVo toVo(ApiUsageLog entity);
}
