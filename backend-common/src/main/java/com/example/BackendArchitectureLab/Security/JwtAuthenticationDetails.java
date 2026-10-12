package com.example.BackendArchitectureLab.Security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

public class JwtAuthenticationDetails extends WebAuthenticationDetails {

    private final String userId;

    public JwtAuthenticationDetails(HttpServletRequest request, String userId) {
        super(request);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
