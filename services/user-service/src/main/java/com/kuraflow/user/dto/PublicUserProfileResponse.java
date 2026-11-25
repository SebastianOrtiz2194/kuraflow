package com.kuraflow.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What any signed-in user may see about another user. Deliberately leaves out the email, auth
 * provider, timezone and plan, which only the owner gets (from {@code GET /api/users/me}).
 */
public record PublicUserProfileResponse(
        UUID id,
        String displayName,
        String avatarUrl,
        UUID preferredLanguageId,
        UUID currentLevelId,
        OffsetDateTime createdAt,
        long followersCount,
        long followingCount
) {
}
