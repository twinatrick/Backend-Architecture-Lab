package com.example.BackendArchitectureLab.Security;

import com.example.BackendArchitectureLab.Util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonSecurityAuditorAware implements AuditorAware<String> {

    private final SecurityUtil securityUtil;

    @Override
    public Optional<String> getCurrentAuditor() {
        return securityUtil.getCurrentAuditor();
    }
}
