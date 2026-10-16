package com.iot.platform.controller;

import com.iot.platform.common.ApiResponse;
import com.iot.platform.model.User;
import com.iot.platform.repository.UserRepository;
import com.iot.platform.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Value("${app.jwt.secret}")
    private String secret;
    @Value("${app.jwt.expire-ms}")
    private long expireMs;

    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody LoginRequest req) {
        Optional<User> u = userRepository.findByUsername(req.getUsername());
        if (!u.isPresent() || !u.get().getPassword().equals(req.getPassword())) {
            return ApiResponse.fail(401, "invalid username or password");
        }
        String token = new JwtUtil(secret).generate(u.get().getUsername(), expireMs);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", u.get().getUsername());
        data.put("role", u.get().getRole());
        return ApiResponse.success(data);
    }
}
