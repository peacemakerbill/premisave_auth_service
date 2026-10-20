package com.premisave.auth.entity;

import com.premisave.auth.enums.LoginMethod;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** One successful sign-in. Failed attempts are kept as security events instead. */
@Data
@Document(collection = "sign_in_history")
@CompoundIndex(name = "user_time", def = "{'userId': 1, 'at': -1}")
public class SignInRecord {

    @Id
    private String id;

    private String userId;
    private String sessionId;
    private String deviceId;
    private String deviceLabel;

    private Instant at;
    private String ipAddress;
    private String country;
    private String region;
    private String city;

    private LoginMethod method;
    private boolean newDevice;
    private int riskScore;
    private List<String> riskReasons;
}
