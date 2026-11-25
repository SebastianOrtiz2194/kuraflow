package com.kuraflow.user.controller;

import com.kuraflow.shared.security.CustomUserDetails;
import com.kuraflow.user.dto.PublicUserProfileResponse;
import com.kuraflow.user.dto.UpdateProfileRequest;
import com.kuraflow.user.dto.UserBriefResponse;
import com.kuraflow.user.dto.UserProfileResponse;
import com.kuraflow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Profile endpoints. A user can only read or change their own private profile ({@code /me});
 * other users' profiles are public views without email or account details.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Endpoints for managing user profiles and preferences")
public class UserController {

    private final UserService userService;

    @GetMapping("/search")
    @Operation(summary = "Search users by display name")
    public ResponseEntity<List<UserBriefResponse>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(userService.searchUsers(q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get another user's public profile (no email or account details)")
    public ResponseEntity<PublicUserProfileResponse> getPublicProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getPublicProfile(id));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user's profile")
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getOwnProfile(userDetails.getId(), userDetails.getEmail()));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current authenticated user's profile")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(userDetails.getId(), userDetails.getEmail(), request));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check for user-service")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("user-service is running");
    }

    @PostMapping("/me/following/{targetId}")
    @Operation(summary = "Follow a user")
    public ResponseEntity<Void> followUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID targetId) {
        userService.followUser(userDetails.getId(), targetId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me/following/{targetId}")
    @Operation(summary = "Unfollow a user")
    public ResponseEntity<Void> unfollowUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID targetId) {
        userService.unfollowUser(userDetails.getId(), targetId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/following")
    @Operation(summary = "Get list of user IDs that this user is following")
    public ResponseEntity<List<UUID>> getFollowingIds(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getFollowingIds(id));
    }

    @GetMapping("/{id}/followers")
    @Operation(summary = "Get list of user IDs that follow this user")
    public ResponseEntity<List<UUID>> getFollowersIds(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getFollowerIds(id));
    }
}
