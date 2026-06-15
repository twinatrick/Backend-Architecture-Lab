package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Dto.Vo.Search.UserSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;

import java.util.List;
import java.util.UUID;

public interface IUserService {
    UserVo createUser(UserVo user);

    List<UserVo> getUser();

    List<UserVo> getUserByEmail(String email);

    UserVo getOnlyUserByEmail(String email);

    UserVo getUserById(String id);

    UserVo saveUser(UserVo user);

    void saveUserWithRole(UserVo userVo);

    List<FunctionVo> getAllParent(List<String> child);

    UserVo getCurrentUserInfo();

    List<UserVo> getAllUsersVo();

    void bindUserProject(String userId, String projectId);

    void rebindUserProjects(UUID userId, List<UUID> projectIds);

    /**
     * 完整覆蓋式綁定使用者角色。空清單清空所有角色，null 清單拋出異常。
     *
     * @param userId 使用者 ID
     * @param roleIds 角色 ID 清單（String 格式）
     */
    void rebindUserRoles(UUID userId, List<String> roleIds);
    
    /**
     * 分頁搜尋使用者
     * 
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<UserVo> searchUsers(UserSearchQuery query);
}
