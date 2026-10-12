package com.zipai.service;

import com.zipai.domain.User;
import com.zipai.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class AuthService {
    private static final String USER_ID = "ZIPAI_USER_ID";
    private final UserRepository users;
    private final PasswordService passwords;

    public AuthService(UserRepository users, PasswordService passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public User required(HttpSession session) {
        Object id = session.getAttribute(USER_ID);
        if (!(id instanceof Long userId)) throw new ResponseStatusException(UNAUTHORIZED, "로그인이 필요합니다.");
        User user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "로그인이 필요합니다."));
        if (!"active".equals(user.status)) throw new ResponseStatusException(UNAUTHORIZED, "사용할 수 없는 계정입니다.");
        return user;
    }

    public User admin(HttpSession session) {
        User user = required(session);
        if (!"admin".equals(user.role)) throw new ResponseStatusException(FORBIDDEN, "관리자 권한이 필요합니다.");
        return user;
    }

    public User current(HttpSession session) {
        try { return required(session); } catch (ResponseStatusException ignored) { return null; }
    }

    @Transactional
    public User signup(Map<String, Object> body, HttpSession session) {
        String username = text(body, "userId");
        String email = text(body, "email").toLowerCase();
        String phone = text(body, "phone").replaceAll("[^0-9]", "");
        String password = text(body, "password");
        if (!username.matches("^[A-Za-z0-9_가-힣]{4,20}$"))
            throw new ResponseStatusException(BAD_REQUEST, "아이디는 한글, 영문, 숫자, 밑줄을 사용해 4~20자로 입력해 주세요.");
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ResponseStatusException(BAD_REQUEST, "올바른 이메일 주소를 입력해 주세요.");
        if (phone.length() < 10 || phone.length() > 11)
            throw new ResponseStatusException(BAD_REQUEST, "올바른 휴대폰 번호를 입력해 주세요.");
        if (password.length() < 8 || password.length() > 72)
            throw new ResponseStatusException(BAD_REQUEST, "비밀번호는 8~72자로 입력해 주세요.");
        if (users.existsByUsernameIgnoreCase(username)) throw new ResponseStatusException(CONFLICT, "이미 사용 중인 아이디입니다.");
        if (users.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(CONFLICT, "이미 가입된 이메일입니다.");
        User user = new User();
        user.username = username; user.email = email; user.phone = phone; user.passwordHash = passwords.encode(password);
        users.save(user);
        session.setAttribute(USER_ID, user.id);
        return user;
    }

    public User login(Map<String, Object> body, HttpSession session) {
        User user = users.findByUsernameIgnoreCase(text(body, "userId"))
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "아이디 또는 비밀번호를 확인해 주세요."));
        if (!"active".equals(user.status) || !passwords.matches(text(body, "password"), user.passwordHash))
            throw new ResponseStatusException(UNAUTHORIZED, "아이디 또는 비밀번호를 확인해 주세요.");
        if (passwords.isLegacy(user.passwordHash)) {
            user.passwordHash = passwords.encode(text(body, "password"));
            users.save(user);
        }
        session.setAttribute(USER_ID, user.id);
        return user;
    }

    public Map<String, Object> publicUser(User user) {
        return Map.of("id", user.username, "userId", user.id, "email", user.email,
            "phone", user.phone, "role", user.role, "createdAt", user.createdAt);
    }

    private static String text(Map<String, Object> body, String key) {
        return String.valueOf(body.getOrDefault(key, "")).trim();
    }
}
