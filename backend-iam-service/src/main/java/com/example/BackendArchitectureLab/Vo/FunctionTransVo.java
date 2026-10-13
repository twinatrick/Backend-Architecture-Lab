package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "批次功能異動傳輸物件")
public class FunctionTransVo {

    @ApiField(description = "待刪除功能清單")
    private List<FunctionVo> toDelete;

    @ApiField(description = "待儲存主要功能清單")
    private List<FunctionVo> toSave;

    @ApiField(description = "待儲存新增子功能清單")
    private List<FunctionVo> toSaveChildren;
}
