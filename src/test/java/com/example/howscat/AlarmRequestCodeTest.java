package com.example.howscat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AlarmManager requestCode 충돌 안나는지 테스트
 * 공식: catId * 1,000,000 + medicationId * 10 + slot
 */
class AlarmRequestCodeTest {

    private int buildRequestCode(long catId, long medicationId, int slot) {
        long seed = catId * 1_000_000L + medicationId * 10L + slot;
        return (int) (Math.abs(seed) % Integer.MAX_VALUE);
    }

    @Test
    void 같은_고양이_약이_다르면_코드도_다름() {
        int code1 = buildRequestCode(1L, 1L, 0);
        int code2 = buildRequestCode(1L, 2L, 0);
        assertNotEquals(code1, code2);
    }

    @Test
    void 같은_약이라도_슬롯이_다르면_코드도_다름() {
        int slot0 = buildRequestCode(1L, 1L, 0);
        int slot1 = buildRequestCode(1L, 1L, 1);
        assertNotEquals(slot0, slot1);
    }

    @Test
    void 고양이가_다르면_코드도_다름() {
        int catA = buildRequestCode(1L, 1L, 0);
        int catB = buildRequestCode(2L, 1L, 0);
        assertNotEquals(catA, catB);
    }

    @Test
    void requestCode가_음수_아닌지_확인() {
        int code = buildRequestCode(Long.MAX_VALUE / 1_000_000L, 999L, 1);
        assertTrue(code >= 0);
        assertTrue(code < Integer.MAX_VALUE);
    }

    @Test
    void catId1_medicationId1_slot0_예상값이_맞는지() {
        // 1*1,000,000 + 1*10 + 0 = 1,000,010
        int code = buildRequestCode(1L, 1L, 0);
        assertEquals(1_000_010, code);
    }
}
