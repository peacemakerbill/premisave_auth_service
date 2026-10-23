package com.premisave.auth.service;

import com.premisave.auth.dto.ReviewResponse;
import com.premisave.auth.dto.SocialActionRequest;
import com.premisave.auth.dto.SocialActionResponse;
import com.premisave.auth.dto.UserDto;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SocialService {

    private final LikeRepository likeRepository;
    private final FollowerRepository followerRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProfileService profileService;
    private final MongoTemplate mongoTemplate;

    public SocialService(LikeRepository likeRepository,
                         FollowerRepository followerRepository,
                         ReviewRepository reviewRepository,
                         UserRepository userRepository,
                         ProfileService profileService,
                         MongoTemplate mongoTemplate) {
        this.likeRepository = likeRepository;
        this.followerRepository = followerRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.mongoTemplate = mongoTemplate;
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

        // Reviews are always stored against the target's real user id, even if the client sent an email
        User target = findUserByIdOrEmail(request.getTargetId());
        if (target == null) {
            return SocialActionResponse.error("REVIEW", "That user could not be found");
        }
        String targetId = target.getId();

        if (user.getId().equals(targetId)) {
            return SocialActionResponse.error("REVIEW", "You cannot review yourself");
        }
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            return SocialActionResponse.error("REVIEW", "Rating must be between 1 and 5");
        }

        if (alreadyReviewed(user, target)) {
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

    /**
     * Reviews written about a user, with each reviewer's details attached. The path value may be the user's id or,
     * for convenience, an email; older reviews that were stored under the email are included.
     */
    public List<ReviewResponse> getUserReviews(String targetRef) {
        User target = findUserByIdOrEmail(targetRef);
        List<Review> reviews = reviewRepository.findByTargetIdIn(targetKeys(target, targetRef));
        return toResponses(reviews, target);
    }

    /** The values a review's targetId may hold for this user: the id, and the email used by older records. */
    private static Set<String> targetKeys(User target, String fallback) {
        Set<String> keys = new LinkedHashSet<>();
        if (target == null) {
            if (fallback != null && !fallback.isBlank()) {
                keys.add(fallback.trim());
            }
            return keys;
        }
        keys.add(target.getId());
        if (target.getEmail() != null && !target.getEmail().isBlank()) {
            keys.add(target.getEmail());
            keys.add(target.getEmail().toLowerCase());
        }
        return keys;
    }

    private boolean alreadyReviewed(User reviewer, User target) {
        for (String key : targetKeys(target, null)) {
            if (reviewRepository.findByUserIdAndTargetId(reviewer.getId(), key).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** A user by id, or by email when the value looks like one. Null when there is no such user. */
    private User findUserByIdOrEmail(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String value = ref.trim();
        if (value.contains("@")) {
            return userRepository.findByEmail(value)
                    .or(() -> userRepository.findByEmail(value.toLowerCase()))
                    .orElse(null);
        }
        return userRepository.findById(value).orElse(null);
    }

    // ====================== REVIEW RESPONSES ======================

    /**
     * Builds the response for a list of reviews. Reviewers are read from the users already attached to the reviews,
     * and the targets that are stored as emails are resolved once each, so no user is looked up twice.
     *
     * @param knownTarget the user every review is about, when the caller already has them (may be null)
     */
    private List<ReviewResponse> toResponses(List<Review> reviews, User knownTarget) {
        Map<String, String> targetIds = new HashMap<>();
        if (knownTarget != null) {
            for (String key : targetKeys(knownTarget, null)) {
                targetIds.put(key, knownTarget.getId());
            }
        }

        List<ReviewResponse> out = new ArrayList<>();
        for (Review review : reviews) {
            ReviewResponse r = new ReviewResponse();
            r.setId(review.getId());
            r.setTargetId(realTargetId(review.getTargetId(), targetIds));
            r.setRating(review.getRating());
            r.setComment(review.getComment());
            r.setCreatedAt(review.getCreatedAt());
            r.setUpdatedAt(review.getUpdatedAt());

            User reviewer = review.getUser();
            if (reviewer != null) {
                r.setUserId(reviewer.getId());
                r.setReviewerName(displayName(reviewer));
                r.setReviewerEmail(reviewer.getEmail());
                r.setReviewerProfilePicture(reviewer.getProfilePictureUrl());
            } else {
                // The reviewer's account is gone: keep who it was, leave the rest empty
                r.setUserId(storedReviewerId(review.getId()));
            }
            out.add(r);
        }
        out.sort(Comparator.comparing(ReviewResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /** The real user id for a stored target value. Emails are resolved once; anything unknown is returned as stored. */
    private String realTargetId(String stored, Map<String, String> cache) {
        if (stored == null || !stored.contains("@")) {
            return stored;
        }
        return cache.computeIfAbsent(stored, key -> {
            User user = findUserByIdOrEmail(key);
            return user == null ? key : user.getId();
        });
    }

    /** First and last name, then the username. Never the email. */
    private static String displayName(User user) {
        StringBuilder name = new StringBuilder();
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            name.append(user.getFirstName().trim());
        }
        if (user.getLastName() != null && !user.getLastName().isBlank()) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(user.getLastName().trim());
        }
        if (name.length() > 0) {
            return name.toString();
        }
        String handle = user.getDisplayUsername();
        return handle == null || handle.equalsIgnoreCase(user.getEmail()) ? null : handle;
    }

    /**
     * The id of the reviewer as stored on a review whose user could not be loaded. Read straight from the
     * stored document, because the loaded entity has no user at all.
     */
    private String storedReviewerId(String reviewId) {
        try {
            org.bson.Document doc = mongoTemplate.findById(reviewId, org.bson.Document.class, "reviews");
            Object ref = doc == null ? null : doc.get("user");
            if (ref instanceof org.bson.Document refDoc) {
                ref = refDoc.get("$id") != null ? refDoc.get("$id") : refDoc.get("_id");
            }
            if (ref instanceof org.bson.types.ObjectId oid) {
                return oid.toHexString();
            }
            return ref == null ? null : ref.toString();
        } catch (RuntimeException e) {
            log.debug("Could not read the stored reviewer of review {}", reviewId);
            return null;
        }
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

    // ====================== FULL USER DATA FOR FRONTEND ======================

    public List<UserDto> getMyLikedUsers() {
        User currentUser = getCurrentUser();
        List<Like> likes = likeRepository.findByUserId(currentUser.getId());

        List<String> targetIds = likes.stream()
                .map(Like::getTargetId)
                .collect(Collectors.toList());

        if (targetIds.isEmpty()) {
            return List.of();
        }

        List<User> likedUsers = userRepository.findAllById(targetIds);

        return likedUsers.stream()
                .filter(user -> user.isActive() && !user.isArchived())
                .map(profileService::convertToPublicDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getMyFollowingUsers() {
        User currentUser = getCurrentUser();
        List<Follower> follows = followerRepository.findByFollowerId(currentUser.getId());

        List<String> targetIds = follows.stream()
                .map(f -> f.getUser().getId())
                .collect(Collectors.toList());

        if (targetIds.isEmpty()) {
            return List.of();
        }

        List<User> followingUsers = userRepository.findAllById(targetIds);

        return followingUsers.stream()
                .filter(user -> user.isActive() && !user.isArchived())
                .map(profileService::convertToPublicDto)
                .collect(Collectors.toList());
    }

    // ====================== INBOUND: WHO LIKED / FOLLOWS / REVIEWED ME ======================

    public List<UserDto> getMyLikers() {
        User currentUser = getCurrentUser();
        List<Like> likes = likeRepository.findByTargetId(currentUser.getId());

        List<String> likerIds = likes.stream()
                .map(like -> like.getUser().getId())
                .collect(Collectors.toList());

        if (likerIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllById(likerIds).stream()
                .filter(user -> user.isActive() && !user.isArchived())
                .map(profileService::convertToPublicDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getMyFollowers() {
        User currentUser = getCurrentUser();
        List<Follower> followers = followerRepository.findByUserId(currentUser.getId());

        List<String> followerIds = followers.stream()
                .map(f -> f.getFollower().getId())
                .collect(Collectors.toList());

        if (followerIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllById(followerIds).stream()
                .filter(user -> user.isActive() && !user.isArchived())
                .map(profileService::convertToPublicDto)
                .collect(Collectors.toList());
    }

    public List<ReviewResponse> getMyReviews() {
        User currentUser = getCurrentUser();
        return toResponses(reviewRepository.findByTargetIdIn(targetKeys(currentUser, null)), currentUser);
    }

    public List<ReviewResponse> getMyWrittenReviews() {
        User currentUser = getCurrentUser();
        return toResponses(reviewRepository.findByUserId(currentUser.getId()), null);
    }

    // ====================== RELATIONSHIP STATUS CHECKS ======================

    public boolean didILikeUser(String targetId) {
        User currentUser = getCurrentUser();
        return likeRepository.findByUserIdAndTargetId(currentUser.getId(), targetId).isPresent();
    }

    public boolean doIFollowUser(String targetId) {
        User currentUser = getCurrentUser();
        // followerRepository: findByUserIdAndFollowerId(userId=target, followerId=me)
        return followerRepository.findByUserIdAndFollowerId(targetId, currentUser.getId()).isPresent();
    }

    public boolean didIReviewUser(String targetRef) {
        User currentUser = getCurrentUser();
        User target = findUserByIdOrEmail(targetRef);
        if (target == null) {
            return reviewRepository.findByUserIdAndTargetId(currentUser.getId(), targetRef).isPresent();
        }
        return alreadyReviewed(currentUser, target);
    }

    public boolean isMutualFollow(String targetId) {
        User currentUser = getCurrentUser();
        String myId = currentUser.getId();
        boolean iFollowThem = followerRepository.findByUserIdAndFollowerId(targetId, myId).isPresent();
        boolean theyFollowMe = followerRepository.findByUserIdAndFollowerId(myId, targetId).isPresent();
        return iFollowThem && theyFollowMe;
    }}