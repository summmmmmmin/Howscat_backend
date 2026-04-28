package com.example.howscat.repository;

import com.example.howscat.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}