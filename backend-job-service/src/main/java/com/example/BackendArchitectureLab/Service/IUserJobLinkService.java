package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.UserJobLinkVo;

import java.util.List;

public interface IUserJobLinkService {

    UserJobLinkVo createUserJobLink(UserJobLinkVo userJobLinkVo);

    List<UserJobLinkVo> getAllUserJobLinks();

    UserJobLinkVo getUserJobLinkById(String id);

    void deleteUserJobLink(String id);

    List<UserJobLinkVo> getUserJobLinksByUserId(String userId);

    List<UserJobLinkVo> getUserJobLinksByJobPostingId(String jobPostingId);

    UserJobLinkVo addJobToCurrentUser(String currentUserId, String jobPostingId);

    void removeJobFromCurrentUser(String currentUserId, String jobPostingId);

    List<UserJobLinkVo> getCurrentUserJobLinks(String currentUserId);
}
