package com.premisave.auth.service;

import com.premisave.auth.dto.SocialActionRequest;
import com.premisave.auth.dto.SocialActionResponse;
import com.premisave.auth.dto.UserInteractionDto;
import com.premisave.auth.entity.Follower;
import com.premisave.auth.entity.Like;
import com.premisave.auth.entity.Review;
import com.premisave.auth.entity.User;
import com.premisave.auth.repository.FollowerRepository;
import com.premisave.auth.repository.LikeRepository;
import com.premisave.auth.repository.ReviewRepository;
import com.premisave.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SocialService {

    private final LikeRepository likeRepository;
    private final FollowerRepository followerRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public SocialService(LikeRepository likeRepository,
                         FollowerRepository followerRepository,
                         ReviewRepository reviewRepository,
                         UserRepository userRepository) {
        this.likeRepository = likeRepository;
        this.followerRepository = followerRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    // ====================== HELPER ======================
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ====================== LIKE ======================
    public SocialActionResponse likeUser(SocialActionRequest request) {
        User user = getCurrentUser();
        String targetId = request.getTargetId();

        if (user.getId().equals(targetId)) {
            return SocialActionResponse.error("LIKE", "You cannot like yourself");
        }

        if (likeRepository.findByUserIdAndTargetId(user.getId(), targetId).isPresent()) {
            return SocialActionResponse.error("LIKE", "You have already liked this user");
        }

        Like like = new Like();
        like.setUser(user);
        like.setTargetId(targetId);
        likeRepository.save(like);

        log.info("User {} liked target {}", user.getId(), targetId);
        return SocialActionResponse.success("LIKE", "User liked successfully");
    }

    public SocialActionResponse unlikeUser(String targetId) {
        User user = getCurrentUser();

        Optional<Like> likeOpt = likeRepository.findByUserIdAndTargetId(user.getId(), targetId);
        if (likeOpt.isEmpty()) {
            return SocialActionResponse.error("UNLIKE", "You have not liked this user");
        }

        likeRepository.delete(likeOpt.get());
        log.info("User {} unliked target {}", user.getId(), targetId);
        return SocialActionResponse.success("UNLIKE", "User unliked successfully");
    }

    // ====================== FOLLOW ======================
    public SocialActionResponse followUser(SocialActionRequest request) {
        User user = getCurrentUser();
        String targetId = request.getTargetId();

        if (user.getId().equals(targetId)) {
            return SocialActionResponse.error("FOLLOW", "You cannot follow yourself");
        }

        if (followerRepository.findByUserIdAndFollowerId(targetId, user.getId()).isPresent()) {
            return SocialActionResponse.error("FOLLOW", "You are already following this user");
        }

        User targetUser = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        Follower follower = new Follower();
        follower.setUser(targetUser);
        follower.setFollower(user);
        followerRepository.save(follower);

        log.info("User {} followed target {}", user.getId(), targetId);
        return SocialActionResponse.success("FOLLOW", "User followed successfully");
    }

    public SocialActionResponse unfollowUser(String targetId) {
        User user = getCurrentUser();

        Optional<Follower> followOpt = followerRepository.findByUserIdAndFollowerId(targetId, user.getId());
        if (followOpt.isEmpty()) {
            return SocialActionResponse.error("UNFOLLOW", "You are not following this user");
        }

        followerRepository.delete(followOpt.get());
        log.info("User {} unfollowed target {}", user.getId(), targetId);
        return SocialActionResponse.success("UNFOLLOW", "User unfollowed successfully");
    }

    // ====================== REVIEW ======================
    public SocialActionResponse reviewUser(SocialActionRequest request) {
        User user = getCurrentUser();
        String targetId = request.getTargetId();

        if (user.getId().equals(targetId)) {
            return SocialActionResponse.error("REVIEW", "You cannot review yourself");
        }
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            return SocialActionResponse.error("REVIEW", "Rating must be between 1 and 5");
        }

        if (reviewRepository.findByUserIdAndTargetId(user.getId(), targetId).isPresent()) {
            return SocialActionResponse.error("REVIEW", "You have already reviewed this user");
        }

        Review review = new Review();
        review.setUser(user);
        review.setTargetId(targetId);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        reviewRepository.save(review);

        log.info("User {} reviewed target {} with {} stars", user.getId(), targetId, request.getRating());
        return SocialActionResponse.success("REVIEW", "Review submitted successfully");
    }

    public SocialActionResponse editReview(SocialActionRequest request) {
        User user = getCurrentUser();
        String reviewId = request.getReviewId();

        if (reviewId == null || reviewId.isBlank()) {
            return SocialActionResponse.error("EDIT_REVIEW", "Review ID is required");
        }

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        if (!review.getUser().getId().equals(user.getId())) {
            return SocialActionResponse.error("EDIT_REVIEW", "You can only edit your own review");
        }

        boolean updated = false;

        if (request.getRating() != null) {
            if (request.getRating() < 1 || request.getRating() > 5) {
                return SocialActionResponse.error("EDIT_REVIEW", "Rating must be between 1 and 5");
            }
            review.setRating(request.getRating());
            updated = true;
        }

        if (request.getComment() != null) {
            review.setComment(request.getComment());
            updated = true;
        }

        if (!updated) {
            return SocialActionResponse.error("EDIT_REVIEW", "No changes provided");
        }

        reviewRepository.save(review);
        log.info("User {} edited review {}", user.getId(), reviewId);
        return SocialActionResponse.success("EDIT_REVIEW", "Review updated successfully");
    }

    public SocialActionResponse deleteReview(String reviewId) {
        User user = getCurrentUser();

        if (reviewId == null || reviewId.isBlank()) {
            return SocialActionResponse.error("DELETE_REVIEW", "Review ID is required");
        }

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        if (!review.getUser().getId().equals(user.getId())) {
            return SocialActionResponse.error("DELETE_REVIEW", "You can only delete your own review");
        }

        reviewRepository.delete(review);
        log.info("User {} deleted review {}", user.getId(), reviewId);
        return SocialActionResponse.success("DELETE_REVIEW", "Review deleted successfully");
    }

    public List<Review> getUserReviews(String targetId) {
        return reviewRepository.findByTargetId(targetId);
    }

    public UserInteractionDto getUserStats(String userId) {
        long followers = followerRepository.countByUserId(userId);
        long following = followerRepository.countByFollowerId(userId);
        long likes = likeRepository.countByTargetId(userId);
        Double avgRating = reviewRepository.getAverageRatingByTargetId(userId);
        int totalReviews = (int) reviewRepository.countByTargetId(userId);

        UserInteractionDto dto = new UserInteractionDto();
        dto.setFollowerCount(followers);
        dto.setFollowingCount(following);
        dto.setLikeCount(likes);
        dto.setAverageRating(avgRating != null ? avgRating : 0.0);
        dto.setTotalReviews(totalReviews);
        return dto;
    }

    // ====================== METHODS FOR FRONTEND STATUS ======================

    public List<String> getUserLikedIds(String userId) {
        List<Like> likes = likeRepository.findByUserId(userId);
        return likes.stream()
                .map(Like::getTargetId)
                .collect(Collectors.toList());
    }

    public List<String> getUserFollowingIds(String userId) {
        List<Follower> follows = followerRepository.findByFollowerId(userId);
        return follows.stream()
                .map(f -> f.getUser().getId())
                .collect(Collectors.toList());
    }
}