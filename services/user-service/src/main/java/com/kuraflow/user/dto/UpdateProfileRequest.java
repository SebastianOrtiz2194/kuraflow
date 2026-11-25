package com.kuraflow.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Partial update: null fields are left unchanged. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProfileRequest {
    @Size(min = 2, max = 100)
    @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
    private String displayName;

    @Size(max = 500)
    @Pattern(regexp = "https://\\S+", message = "must be an https:// URL")
    private String avatarUrl;

    private UUID preferredLanguageId;
    private UUID currentLevelId;

    /** IANA zone id such as {@code Asia/Tokyo}; checked against the JDK's zone list by the service. */
    @Size(max = 50)
    private String timezone;
}
