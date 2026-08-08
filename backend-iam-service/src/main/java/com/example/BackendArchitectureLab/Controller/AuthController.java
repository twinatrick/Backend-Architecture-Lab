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
@ApiControllerTag(name = "Auth", description = "身分驗證與註冊相關 API")
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
    @ApiOperationBadRequest(summary = "註冊新使用者", description = "建立使用者帳號並回傳 JWT access token。")
    public ResponseType<Token> signup(@RequestBody SignupRequest request) throws JoseException {
        authService.signup(request);

        String token = jwtUtils.generateJWT(request.getEmail());

        httpResponse.addHeader("Authorization", "Bearer " + token);
        Token res = new Token();
        res.setAccessToken(token);
        return new ResponseType<>(0, res, "使用者註冊成功");
    }

    @Ignore
    @PostMapping("/login")
    @ApiOperationAuth(summary = "使用者登入", description = "驗證使用者憑證並回傳 JWT access token。")
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
        return new ResponseType<>(0, res, "登入成功");
    }

    @Ignore
    @PostMapping("/superuser")
    @ApiOperationBadRequest(summary = "建立超級使用者", description = "當提供的金鑰符合設定時建立管理員帳號。")
    public ResponseType<?> createSuperUser(@RequestBody SuperUserRequest request) {
        authService.createSuperUser(request);

        String email = (request.getEmail() == null || request.getEmail().isBlank()) ? "admin" : request.getEmail();
        HashMap<String, String> res = new HashMap<>();
        res.put("email", email);
        res.put("password", "admin");
        return new ResponseType<>(0, res, "超級使用者建立成功");
    }
}
