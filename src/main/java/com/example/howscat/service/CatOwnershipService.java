package com.example.howscat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatOwnershipService {

    private final JdbcTemplate jdbcTemplate;

    public void assertOwner(Long catId, Integer userId) {
        Long ownerUserId = jdbcTemplate.query(
                "SELECT user_id FROM cat WHERE cat_id = ?",
                new Object[]{catId},
                rs -> rs.next() ? rs.getLong("user_id") : null
        );
        if (ownerUserId == null) throw new IllegalArgumentException("cat not found");
        if (ownerUserId.longValue() != userId.longValue()) throw new SecurityException("cat does not belong to user");
    }
}
