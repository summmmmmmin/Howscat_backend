package com.example.howscat.repository;

import com.example.howscat.domain.Cat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatRepository extends JpaRepository<Cat, Long> {
    // User 엔티티의 userId를 기준으로 모든 Cat 가져오기
    List<Cat> findAllByUser_UserId(Integer userId);
}