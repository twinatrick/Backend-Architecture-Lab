package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationAuth;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.Ignore;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Security.JwtAuthenticationToken;
import com.example.BackendArchitectureLab.Service.IAuthService;
import com.example.BackendArchitectureLab.Vo.LoginRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.SignupRequest;
import com.example.BackendArchitectureLab.Vo.SuperUserRequest;
import com.example.BackendArchitectureLab.Vo.Response.Token;
import jakarta.servlet.http.HttpServletResponse;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;

@RestController
@RequestMapping("/auth")
@ApiControllerTag(name = "Auth", description = "Backend API endpoints - Authentication and registration")
public class AuthController {

    @Autowired
    private IAuthService authService;

    @Autowired
    private HttpServletResponse httpResponse;

    @Autowired
    private JwtAuthenticationToken jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Ignore
    @PostMapping("/signup")
    @ApiOperationBadRequest(summary = "Register a new user", description = "Creates a user account and returns a JWT access token.")
    public ResponseType<Token> signup(@RequestBody SignupRequest request) throws JoseException {
        authService.signup(request);

        String token = jwtUtils.generateJWT(request.getEmail());

        httpResponse.addHeader("Authorization", "Bearer " + token);
        Token res = new Token();
        res.setAccessToken(token);
        return new ResponseType<>(0, res, "User registered successfully");
    }

    @Ignore
    @PostMapping("/login")
    @ApiOperationAuth(summary = "User login", description = "Authenticates user credentials and returns a JWT access token.")
    public ResponseType<Token> login(@RequestBody LoginRequest request) throws JoseException {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AuthenticationException e) {
            throw new AppException("AUTH_ERROR", "Invalid username or password", 401);
        }

        String token = jwtUtils.generateJWT(request.getEmail());

        httpResponse.addHeader("Authorization", "Bearer " + token);
        Token res = new Token();
        res.setAccessToken(token);
        return new ResponseType<>(0, res, "Login successful");
    }

    @Ignore
    @PostMapping("/superuser")
    @ApiOperationBadRequest(summary = "Create super user", description = "Creates an admin user when the provided key matches configuration.")
    public ResponseType<?> createSuperUser(@RequestBody SuperUserRequest request) {
        authService.createSuperUser(request);

        String email = (request.getEmail() == null || request.getEmail().isBlank()) ? "admin" : request.getEmail();
        HashMap<String, String> res = new HashMap<>();
        res.put("email", email);
        res.put("password", "admin");
        return new ResponseType<>(0, res, "Super user created");
    }
}
