package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationAuth;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.Ingnore;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Security.JwtAuthenticationToken;
import com.example.BackendArchitectureLab.Vo.LoginRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Vo.SignupRequest;
import com.example.BackendArchitectureLab.Vo.SuperUserRequest;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Vo.UserVo;
import jakarta.servlet.http.HttpServletResponse;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.example.BackendArchitectureLab.Vo.Response.Token;
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
import java.util.List;

@RestController
@RequestMapping("/auth")
@ApiControllerTag(name = "Auth", description = "Backend API endpoints - Authentication and registration")
public class AuthController {

    @Autowired
    private IUserService userService;

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    @Autowired
    private HttpServletResponse httpResponse;

    @Autowired
    private JwtAuthenticationToken jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Value("${superuser.key}")
    private String superUserKey;

    @Ingnore
    @PostMapping("/signup")
    @ApiOperationBadRequest(summary = "Register a new user", description = "Creates a user account and returns a JWT access token.")
    public ResponseType<Token> signup(@RequestBody SignupRequest request) throws JoseException {
        List<UserVo> existingUsers = userService.getUserByEmail(request.getEmail());
        if (!existingUsers.isEmpty()) {
            throw new AppException("VALIDATION_ERROR", "User already exists", 400);
        }

        UserVo userVo = new UserVo();
        userVo.setEmail(request.getEmail());
        userVo.setPassword(request.getPassword());
        userVo.setName(request.getEmail());
        UserVo savedUser = userService.createUser(userVo);

        String token = jwtUtils.generateJWT(request.getEmail());

        List<RoleOutVo> roles = userServiceFeignClient.getAllRoles();
        RoleOutVo defaultRole = roles.stream()
                .filter(role -> "user".equalsIgnoreCase(role.getName()))
                .findFirst().orElse(null);
        if (defaultRole != null) {
            userServiceFeignClient.userBindRole(String.valueOf(savedUser.getId()), String.valueOf(defaultRole.getId()));
        }

        httpResponse.addHeader("Authorization", "Bearer " + token);
        Token res = new Token();
        res.setAccessToken(token);
        return new ResponseType<>(0, res, "User registered successfully");
    }

    @Ingnore
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

    @Ingnore
    @PostMapping("/superuser")
    @ApiOperationBadRequest(summary = "Create super user", description = "Creates an admin user when the provided key matches configuration.")
    public ResponseType<?> createSuperUser(@RequestBody SuperUserRequest request) {
        if (request.getKey() == null || !request.getKey().equals(superUserKey)) {
            throw new AppException("VALIDATION_ERROR", "Invalid key", 400);
        }
        String email = (request.getEmail() == null || request.getEmail().isBlank()) ? "admin" : request.getEmail();
        List<UserVo> existingUsers = userService.getUserByEmail(email);
        if (!existingUsers.isEmpty()) {
            throw new AppException("VALIDATION_ERROR", "User already exists", 400);
        }

        UserVo userVo = new UserVo();
        userVo.setEmail(email);
        userVo.setPassword("admin");
        userVo.setName(email);
        UserVo savedUser = userService.createUser(userVo);

        RoleOutVo adminRole = userServiceFeignClient.getRoleByName("admin");
        if (adminRole == null) {
            RoleOutVo role = new RoleOutVo();
            role.setName("admin");
            adminRole = userServiceFeignClient.addRole(role);
        }
        userServiceFeignClient.userBindRole(String.valueOf(savedUser.getId()), String.valueOf(adminRole.getId()));

        HashMap<String, String> res = new HashMap<>();
        res.put("email", email);
        res.put("password", "admin");
        return new ResponseType<>(0, res, "Super user created");
    }
}
