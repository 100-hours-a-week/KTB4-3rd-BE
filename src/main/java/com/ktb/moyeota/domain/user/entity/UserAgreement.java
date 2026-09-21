package com.ktb.moyeota.domain.user.entity;

import com.ktb.moyeota.domain.user.model.AgreementsCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "user_agreements",
        uniqueConstraints = @UniqueConstraint(name = "uk_agreements_user", columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAgreement {

    public static final int CURRENT_TERM_VERSION = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "term_version", nullable = false)
    private int termVersion;

    @Column(name = "required_agreed_at", nullable = false)
    private LocalDateTime requiredAgreedAt;

    @Column(name = "account_third_party_agreed", nullable = false)
    private boolean accountThirdPartyAgreed;

    @Column(name = "account_third_party_changed_at", nullable = false)
    private LocalDateTime accountThirdPartyChangedAt;

    @Column(name = "marketing_agreed", nullable = false)
    private boolean marketingAgreed;

    @Column(name = "marketing_changed_at", nullable = false)
    private LocalDateTime marketingChangedAt;

    private UserAgreement(User user, AgreementsCommand agreements, LocalDateTime now) {
        this.user = user;
        this.termVersion = CURRENT_TERM_VERSION;
        this.requiredAgreedAt = now;
        this.accountThirdPartyAgreed = agreements.accountThirdParty();
        this.accountThirdPartyChangedAt = now;
        this.marketingAgreed = agreements.marketing();
        this.marketingChangedAt = now;
    }

    public static UserAgreement agree(User user, AgreementsCommand agreements, LocalDateTime now) {
        return new UserAgreement(user, agreements, now);
    }
}
