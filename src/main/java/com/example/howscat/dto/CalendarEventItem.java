package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventItem {

    private Long id;
    private String type;

    private String date; // yyyy-MM-dd
    private String time; // optional

    private String title;
    private String subtitle;
    private String imagePath;
    private String riskLevel;
    private String vomitColor;

    /** 토 분석 안내 문구(vomit_status.guide_text) */
    private String guideText;

    /** 건강 일정과 같은 날짜에 연결된 메모(calendar_memo) 내용 */
    private String scheduleMemo;

    private Boolean alarmEnabled;

    /** 건강 일정과 연결된 calendar_memo.id (메모 수정 시 사용) */
    private Long linkedMemoId;

    /** 건강 일정 종류(메모 추가 시 health_type 연결) */
    private Long healthTypeId;
}
