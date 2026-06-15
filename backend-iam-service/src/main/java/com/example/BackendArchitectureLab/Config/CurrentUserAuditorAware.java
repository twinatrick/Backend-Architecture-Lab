package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Security.CustomUserDetails;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUserAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            if (userDetails.getUser() != null && userDetails.getUser().getId() != null) {
                return Optional.of(userDetails.getUser().getId().toString());
            }
        }
        return Optional.empty();
    }
}
