package com.zipai.web;

import com.zipai.domain.User;
import com.zipai.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @GetMapping("/me")
    Map<String, Object> me(HttpSession session) {
        User user = auth.current(session);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", user != null);
        result.put("user", user == null ? null : auth.publicUser(user));
        return result;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> signup(@RequestBody Map<String, Object> body, HttpSession session) {
        return Map.of("user", auth.publicUser(auth.signup(body, session)));
    }

    @PostMapping("/login")
    Map<String, Object> login(@RequestBody Map<String, Object> body, HttpSession session) {
        return Map.of("user", auth.publicUser(auth.login(body, session)));
    }

    @PostMapping("/logout")
    Map<String, Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return Map.of("success", true);
    }
}
