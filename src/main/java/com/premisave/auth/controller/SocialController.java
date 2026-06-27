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
import java.util.Map;

@RestController
@RequestMapping("/social")
@PreAuthorize("isAuthenticated()")
public class SocialController {

    private final SocialService socialService;
    public SocialController(SocialService socialService, ProfileService profileService) {
        this.socialService = socialService;
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

    // ── Inbound: who liked / follows / reviewed ME ────────────────────────────

    @GetMapping("/my-likers")
    public ResponseEntity<List<UserDto>> getMyLikers() {
        return ResponseEntity.ok(socialService.getMyLikers());
    }

    @GetMapping("/my-followers")
    public ResponseEntity<List<UserDto>> getMyFollowers() {
        return ResponseEntity.ok(socialService.getMyFollowers());
    }

    @GetMapping("/my-reviews")
    public ResponseEntity<List<Review>> getMyReviews() {
        return ResponseEntity.ok(socialService.getMyReviews());
    }

    @GetMapping("/my-written-reviews")
    public ResponseEntity<List<Review>> getMyWrittenReviews() {
        return ResponseEntity.ok(socialService.getMyWrittenReviews());
    }

    // ── Relationship status checks ────────────────────────────────────────────

    @GetMapping("/like/status/{targetId}")
    public ResponseEntity<Map<String, Boolean>> getLikeStatus(@PathVariable String targetId) {
        return ResponseEntity.ok(Map.of("liked", socialService.didILikeUser(targetId)));
    }

    @GetMapping("/follow/status/{targetId}")
    public ResponseEntity<Map<String, Boolean>> getFollowStatus(@PathVariable String targetId) {
        return ResponseEntity.ok(Map.of("following", socialService.doIFollowUser(targetId)));
    }

    @GetMapping("/review/status/{targetId}")
    public ResponseEntity<Map<String, Boolean>> getReviewStatus(@PathVariable String targetId) {
        return ResponseEntity.ok(Map.of("reviewed", socialService.didIReviewUser(targetId)));
    }

    @GetMapping("/follow/mutual/{targetId}")
    public ResponseEntity<Map<String, Boolean>> getMutualFollowStatus(@PathVariable String targetId) {
        return ResponseEntity.ok(Map.of("mutual", socialService.isMutualFollow(targetId)));
    }
}