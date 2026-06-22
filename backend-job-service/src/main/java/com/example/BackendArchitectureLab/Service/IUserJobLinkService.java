package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.UserJobLinkVo;

import java.util.List;

public interface IUserJobLinkService {

    UserJobLinkVo createUserJobLink(UserJobLinkVo userJobLinkVo);

    List<UserJobLinkVo> getAllUserJobLinks();
    CacheListWrapper<UserJobLinkVo> getAllUserJobLinksCache();

    UserJobLinkVo getUserJobLinkById(String id);

    void deleteUserJobLink(String id);

    List<UserJobLinkVo> getUserJobLinksByUserId(String userId);
    CacheListWrapper<UserJobLinkVo> getUserJobLinksByUserIdCache(String userId);

    List<UserJobLinkVo> getUserJobLinksByJobPostingId(String jobPostingId);
    CacheListWrapper<UserJobLinkVo> getUserJobLinksByJobPostingIdCache(String jobPostingId);

    UserJobLinkVo updateUserJobLink(UserJobLinkVo userJobLinkVo);

    UserJobLinkVo addJobToCurrentUser(String currentUserId, String jobPostingId);

    void removeJobFromCurrentUser(String currentUserId, String jobPostingId);

    List<UserJobLinkVo> getCurrentUserJobLinks(String currentUserId);
    CacheListWrapper<UserJobLinkVo> getCurrentUserJobLinksCache(String currentUserId);
}
