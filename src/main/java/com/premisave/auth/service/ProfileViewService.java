package com.premisave.auth.service;

import com.premisave.auth.dto.ProfileViewResponse;
import com.premisave.auth.dto.ProfileViewStats;
import com.premisave.auth.entity.ProfileView;
import com.premisave.auth.entity.User;
import com.premisave.auth.repository.ProfileViewRepository;
import com.premisave.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProfileViewService {

    private final ProfileViewRepository profileViewRepository;
    private final UserRepository userRepository;

    public ProfileViewService(ProfileViewRepository profileViewRepository, UserRepository userRepository) {
        this.profileViewRepository = profileViewRepository;
        this.userRepository = userRepository;
    }

    public ProfileViewResponse recordProfileView(String targetId, HttpServletRequest request) {
        User viewer = getCurrentUser();

        if (viewer.getId().equals(targetId)) {
            return new ProfileViewResponse(null, null, null, null, null, null, null, null, "You cannot view your own profile");
        }

        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
        if (profileViewRepository.findRecentView(new ObjectId(viewer.getId()), new ObjectId(targetId), oneDayAgo).isPresent()) {
            return new ProfileViewResponse(null, null, null, null, null, null, null, null, "Profile view already recorded recently");
        }

        ProfileView view = new ProfileView();
        view.setViewer(viewer);
        view.setTarget(target);
        view.setIpAddress(request.getRemoteAddr());
        view.setUserAgent(request.getHeader("User-Agent"));
        view.setDeviceType(detectDeviceType(request));
        view.setSource(detectSource(request));
        view.setAnonymous(false);

        ProfileView saved = profileViewRepository.save(view);

        log.info("Profile viewed: {} → {}", viewer.getDisplayUsername(), target.getDisplayUsername());

        return convertToResponse(saved);
    }

    public List<ProfileViewResponse> getWhoViewedMyProfile() {
        User user = getCurrentUser();
        List<ProfileView> views = profileViewRepository.findTop20ByTargetIdOrderByViewedAtDesc(new ObjectId(user.getId()));
        return views.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    public ProfileViewStats getProfileViewStats() {
        User user = getCurrentUser();
        ObjectId userId = new ObjectId(user.getId());
        long total = profileViewRepository.countByTargetId(userId);
        long last7Days = profileViewRepository.countViewsInLastDays(userId, LocalDateTime.now().minusDays(7));
        long last30Days = profileViewRepository.countViewsInLastDays(userId, LocalDateTime.now().minusDays(30));
        int uniqueViewers = profileViewRepository.countUniqueViewers(user.getId());

        return new ProfileViewStats(total, last7Days, last30Days, uniqueViewers, "Profile statistics retrieved successfully");
    }

    private User getCurrentUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private String detectDeviceType(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "UNKNOWN";
        if (ua.contains("Mobile") || ua.contains("Android") || ua.contains("iPhone")) return "MOBILE";
        if (ua.contains("Tablet") || ua.contains("iPad")) return "TABLET";
        return "DESKTOP";
    }

    private String detectSource(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) return "DIRECT";
        if (referer.contains("/search")) return "SEARCH";
        if (referer.contains("/suggested")) return "SUGGESTED";
        return "DIRECT";
    }

    private ProfileViewResponse convertToResponse(ProfileView view) {
        return new ProfileViewResponse(
                view.getId(),
                view.getViewer().getId(),
                view.getViewer().getFirstName() + " " + view.getViewer().getLastName(),
                view.getViewer().getProfilePictureUrl(),
                view.getTarget().getId(),
                view.getViewedAt(),
                view.getSource(),
                view.getDeviceType(),
                "Success"
        );
    }
}