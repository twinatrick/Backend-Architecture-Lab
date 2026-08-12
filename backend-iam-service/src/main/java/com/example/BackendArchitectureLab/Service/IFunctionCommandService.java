package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.FunctionVo;

import java.util.List;

public interface IFunctionCommandService {
    FunctionVo addFunction(FunctionVo functionVo);

    void updateFunction(FunctionVo functionVo);

    void deleteFunction(FunctionVo functionVo);

    void deleteFunction(List<FunctionVo> function);

    void saveFunction(List<FunctionVo> function);
}
