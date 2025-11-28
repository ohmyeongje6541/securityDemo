package org.example.sequritydemo.service;

import java.util.Optional;
import org.example.sequritydemo.entity.User;

public interface UserService {

    // 사용자 조회
    Optional<User> findByUsername(String username);
    Optional<User> findById(Long id);

    // username 중복확인
    boolean existsByUsername(String username);

}
