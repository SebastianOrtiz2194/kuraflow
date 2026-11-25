package com.kuraflow.user.service;

import com.kuraflow.user.dto.PublicUserProfileResponse;
import com.kuraflow.user.dto.UpdateProfileRequest;
import com.kuraflow.user.dto.UserBriefResponse;
import com.kuraflow.user.dto.UserProfileResponse;
import com.kuraflow.user.entity.User;
import com.kuraflow.user.entity.UserFollow;
import com.kuraflow.user.entity.UserFollowId;
import com.kuraflow.user.exception.UserNotFoundException;
import com.kuraflow.user.repository.UserFollowRepository;
import com.kuraflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserFollowRepository userFollowRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("getOwnProfile: Returns existing user profile with follower counts")
    void getOwnProfile_ExistingUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@kuraflow.com")
                .displayName("Kura Learner")
                .avatarUrl("https://avatar.png")
                .timezone("UTC")
                .isPremium(false)
                .createdAt(OffsetDateTime.now())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userFollowRepository.countByFollowedId(userId)).thenReturn(10L);
        when(userFollowRepository.countByFollowerId(userId)).thenReturn(5L);

        UserProfileResponse response = userService.getOwnProfile(userId, "user@kuraflow.com");

        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("user@kuraflow.com");
        assertThat(response.getFollowersCount()).isEqualTo(10L);
        assertThat(response.getFollowingCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getOwnProfile: Provisions a missing profile with the email from the token")
    void getOwnProfile_AutoProvisionsIfMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileResponse response = userService.getOwnProfile(userId, "real@kuraflow.com");

        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("real@kuraflow.com");
    }

    @Test
    @DisplayName("getPublicProfile: Returns the public view of an existing user")
    void getPublicProfile_ExistingUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).email("private@kuraflow.com").displayName("Bob").build()));
        when(userFollowRepository.countByFollowedId(userId)).thenReturn(2L);
        when(userFollowRepository.countByFollowerId(userId)).thenReturn(1L);

        PublicUserProfileResponse response = userService.getPublicProfile(userId);

        assertThat(response.displayName()).isEqualTo("Bob");
        assertThat(response.followersCount()).isEqualTo(2L);
        assertThat(response.toString()).doesNotContain("private@kuraflow.com");
    }

    @Test
    @DisplayName("getPublicProfile: Unknown ids are 404 and never create a user row")
    void getPublicProfile_UnknownUserIsNotProvisioned() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getPublicProfile(userId)).isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile: Updates fields correctly")
    void updateProfile_Success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@kuraflow.com")
                .displayName("Old Name")
                .timezone("UTC")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("  New Name ");
        request.setTimezone("Asia/Tokyo");

        UserProfileResponse response = userService.updateProfile(userId, "user@kuraflow.com", request);

        assertThat(response.getDisplayName()).isEqualTo("New Name");
        assertThat(response.getTimezone()).isEqualTo("Asia/Tokyo");
    }

    @Test
    @DisplayName("updateProfile: Rejects timezones that aren't IANA zone ids")
    void updateProfile_RejectsUnknownTimezone() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).timezone("UTC").build()));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setTimezone("Mars/Olympus_Mons");

        assertThatThrownBy(() -> userService.updateProfile(userId, "user@kuraflow.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown timezone");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("searchUsers: Returns matching brief user profiles")
    void searchUsers_Success() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .displayName("Alice in Wonderland")
                .avatarUrl("https://alice.png")
                .build();

        when(userRepository.findByDisplayNameContainingIgnoreCase("alice")).thenReturn(List.of(user));

        List<UserBriefResponse> results = userService.searchUsers("alice");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDisplayName()).isEqualTo("Alice in Wonderland");
    }

    @Test
    @DisplayName("followUser: Successfully saves follow relation")
    void followUser_Success() {
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        UserFollowId followId = new UserFollowId(followerId, followedId);

        when(userRepository.existsById(followedId)).thenReturn(true);
        when(userFollowRepository.existsById(followId)).thenReturn(false);

        userService.followUser(followerId, followedId);

        ArgumentCaptor<UserFollow> saved = ArgumentCaptor.forClass(UserFollow.class);
        verify(userFollowRepository).save(saved.capture());
        assertThat(saved.getValue().getFollowedId()).isEqualTo(followedId);
    }

    @Test
    @DisplayName("followUser: Following an unknown user is 404 instead of a foreign-key error")
    void followUser_UnknownTarget() {
        UUID followedId = UUID.randomUUID();
        when(userRepository.existsById(followedId)).thenReturn(false);

        assertThatThrownBy(() -> userService.followUser(UUID.randomUUID(), followedId))
                .isInstanceOf(UserNotFoundException.class);
        verify(userFollowRepository, never()).save(any());
    }

    @Test
    @DisplayName("followUser: Throws exception when attempting to follow self")
    void followUser_SelfFollowThrows() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> userService.followUser(userId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot follow yourself");
    }
}
