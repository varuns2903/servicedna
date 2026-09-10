package com.servicedna.oncall.dto;

import java.time.LocalDate;
import java.util.List;

public record OnCallRotationDto(
    int rotationLengthDays,
    LocalDate startDate,
    List<OnCallMemberDto> members,
    OnCallMemberDto currentOnCall,
    LocalDate currentShiftEndsOn) {}
