package com.ktb.moyeota.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private static final String WITHDRAWN_NICKNAME_PREFIX = "탈퇴한사용자_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "bank_name", length = 30)
    private String bankName;

    @Column(name = "account_no")
    private byte[] accountNo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    private User(String name, String nickname, Gender gender, String profileImageUrl) {
        this.name = name;
        this.nickname = nickname;
        this.gender = gender;
        this.profileImageUrl = profileImageUrl;
    }

    public static User register(String name, String nickname, Gender gender, String profileImageUrl) {
        return new User(name, nickname, gender, profileImageUrl);
    }

    public void registerBankAccount(String bankName, byte[] encryptedAccountNo) {
        this.bankName = bankName;
        this.accountNo = encryptedAccountNo;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public boolean hasBankAccount() {
        return bankName != null && accountNo != null;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    public void withdraw(LocalDateTime now) {
        this.withdrawnAt = now;
        this.nickname = WITHDRAWN_NICKNAME_PREFIX + id;
        this.profileImageUrl = null;
        this.bankName = null;
        this.accountNo = null;
    }
}
