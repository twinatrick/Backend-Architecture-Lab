package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.SignupRequest;
import com.example.BackendArchitectureLab.Vo.SuperUserRequest;
import com.example.BackendArchitectureLab.Vo.UserVo;

import java.util.List;

/**
 * 認證與權限組合服務：負責註冊、超級使用者建立與當前使用者權限組合。
 * UserService 保持純 CRUD，本服務負責跨使用者/角色/權限的組合邏輯。
 */
public interface IAuthService {

    /**
     * 取得當前登入使用者的完整資訊（含父層權限補全）。
     *
     * @return 使用者資訊
     */
    UserVo getCurrentUserInfo();

    /**
     * 依子權限清單補全父層權限。
     *
     * @param child 子權限 ID 清單
     * @return 父層權限清單
     */
    List<FunctionVo> getAllParent(List<String> child);

    /**
     * 註冊新使用者並綁定預設角色（user）。
     *
     * @param request 註冊請求
     * @return 建立的使用者
     */
    UserVo signup(SignupRequest request);

    /**
     * 以 superuser key 建立管理員帳號並綁定 admin 角色。
     *
     * @param request 超管建立請求（含 key 與 email）
     * @return 建立的使用者
     */
    UserVo createSuperUser(SuperUserRequest request);
}
