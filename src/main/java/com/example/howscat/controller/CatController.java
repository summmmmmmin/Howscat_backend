package com.example.howscat.controller;

import com.example.howscat.domain.Cat;
import com.example.howscat.domain.User;
import com.example.howscat.dto.CatRequest;
import com.example.howscat.dto.CatResponse;
import com.example.howscat.repository.CatRepository;
import com.example.howscat.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@RequiredArgsConstructor
@RequestMapping("/cats")
public class CatController {

    private final CatRepository catRepository;
    private final UserRepository userRepository;

    private Integer extractUserId(Authentication authentication) {
        return (Integer) authentication.getPrincipal();
    }

    @PostMapping
    public ResponseEntity<?> register(
            @RequestBody @Valid CatRequest request,
            Authentication authentication) {

        Integer userId = extractUserId(authentication);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Cat cat = Cat.builder()
                .name(request.getName())
                .gender(request.getGender())
                .birthDate(LocalDate.parse(request.getBirthDate()))
                .user(user)
                .build();

        catRepository.save(cat);

        // 첫 등록 고양이면 last_viewed_cat_id 세팅
        if (user.getLastViewedCatId() == null) {
            user.setLastViewedCatId(cat.getId());
            userRepository.save(user);
        }

        return ResponseEntity.ok(
                Map.of("success", true, "message", "고양이 등록 성공","catId", cat.getId())
        );
    }

    @GetMapping("/{catId}")
    public ResponseEntity<?> getCat(
            @PathVariable Long catId,
            Authentication authentication) {

        Integer userId = extractUserId(authentication);

        Cat cat = catRepository.findById(catId)
                .orElseThrow(() -> new IllegalArgumentException("고양이를 찾을 수 없습니다."));

        if (!cat.getUser().getUserId().equals(userId)) {
            throw new SecurityException("해당 고양이에 대한 권한이 없습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 🔥 여기서 마지막 조회 고양이 업데이트
        user.updateLastViewedCatId(cat.getId());
        userRepository.save(user);

        CatResponse response = new CatResponse(cat);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUserCats(Authentication authentication) {
        Integer userId = extractUserId(authentication);

        List<Cat> cats = catRepository.findAllByUser_UserId(userId);

        List<CatResponse> response = cats.stream()
                .map(CatResponse::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/select/{catId}")
    public ResponseEntity<?> selectCat(@PathVariable Long catId,
                                       Authentication authentication) {
        Integer userId = extractUserId(authentication);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setLastViewedCatId(catId);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

}
