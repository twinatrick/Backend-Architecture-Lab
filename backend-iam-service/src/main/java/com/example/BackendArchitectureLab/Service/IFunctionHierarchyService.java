package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.FunctionVo;

import java.util.List;

public interface IFunctionHierarchyService {
    List<FunctionVo> saveFunctionNewChild(List<FunctionVo> function);
}
