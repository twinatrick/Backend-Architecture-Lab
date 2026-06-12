package com.example.BackendArchitectureLab.Service;

import java.util.List;

public interface IInitAndCheckService {
    void initAndCheck();

    void checkRole();

    boolean checkIsExist(String oneLayer, String twoLayer, String threeLayer);

    void checkFunctionBindDefaultRole();

    void insertFunctionByList(List<String> functionList, String parent);
}
