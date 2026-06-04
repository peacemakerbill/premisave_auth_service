package com.premisave.auth.controller;

import com.premisave.auth.dto.SocialActionRequest;
import com.premisave.auth.dto.SocialActionResponse;
import com.premisave.auth.dto.UserDto;
import com.premisave.auth.dto.UserInteractionDto;
import com.premisave.auth.entity.Review;
import com.premisave.auth.service.ProfileService;
import com.premisave.auth.service.SocialService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/social")
@PreAuthorize("isAuthenticated()")
public class SocialController {

    private final SocialService socialService;
    private final ProfileService profileService;

    public SocialController(SocialService socialService, ProfileService profileService) {
        this.socialService = socialService;
        this.profileService = profileService;
    }

    @PostMapping("/like")
    public ResponseEntity<SocialActionResponse> likeUser(@Valid @RequestBody SocialActionRequest request) {
        return ResponseEntity.ok(socialService.likeUser(request));
    }

    @DeleteMapping("/unlike/{targetId}")
    public ResponseEntity<SocialActionResponse> unlikeUser(@PathVariable String targetId) {
        return ResponseEntity.ok(socialService.unlikeUser(targetId));
    }

    @PostMapping("/follow")
    public ResponseEntity<SocialActionResponse> followUser(@Valid @RequestBody SocialActionRequest request) {
        return ResponseEntity.ok(socialService.followUser(request));
    }

    @DeleteMapping("/unfollow/{targetId}")
    public ResponseEntity<SocialActionResponse> unfollowUser(@PathVariable String targetId) {
        return ResponseEntity.ok(socialService.unfollowUser(targetId));
    }

    @PostMapping("/review")
    public ResponseEntity<SocialActionResponse> reviewUser(@Valid @RequestBody SocialActionRequest request) {
        return ResponseEntity.ok(socialService.reviewUser(request));
    }

    @PutMapping("/review")
    public ResponseEntity<SocialActionResponse> editReview(@Valid @RequestBody SocialActionRequest request) {
        return ResponseEntity.ok(socialService.editReview(request));
    }

    @DeleteMapping("/review/{reviewId}")
    public ResponseEntity<SocialActionResponse> deleteReview(@PathVariable String reviewId) {
        return ResponseEntity.ok(socialService.deleteReview(reviewId));
    }

    @GetMapping("/reviews/{targetId}")
    public ResponseEntity<List<Review>> getUserReviews(@PathVariable String targetId) {
        List<Review> reviews = socialService.getUserReviews(targetId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/stats/{userId}")
    public ResponseEntity<UserInteractionDto> getUserStats(@PathVariable String userId) {
        return ResponseEntity.ok(socialService.getUserStats(userId));
    }


    @GetMapping("/my-likes")
    public ResponseEntity<List<UserDto>> getMyLikes() {
        List<UserDto> likedUsers = socialService.getMyLikedUsers();
        return ResponseEntity.ok(likedUsers);
    }

    @GetMapping("/my-following")
    public ResponseEntity<List<UserDto>> getMyFollowing() {
        List<UserDto> followingUsers = socialService.getMyFollowingUsers();
        return ResponseEntity.ok(followingUsers);
    }
}