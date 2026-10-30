package com.example.BackendArchitectureLab.Security;

import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Util.IamLocalCacheManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final IUserDataAccess userDataAccess;
    private final IamLocalCacheManager iamLocalCacheManager;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDetails cached = iamLocalCacheManager.getUserDetails(username);
        if (cached != null) {
            return cached;
        }
        User user = userDataAccess.findByEmailWithRoles(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
        CustomUserDetails details = new CustomUserDetails(user);
        iamLocalCacheManager.putUserDetails(username, details);
        return details;
    }

    public void evict(String username) {
        if (username != null) {
            iamLocalCacheManager.evictUser(username);
        }
    }

    public void evictAll() {
        iamLocalCacheManager.evictAll();
    }
}
