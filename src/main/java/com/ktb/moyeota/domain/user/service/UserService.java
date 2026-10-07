package com.ktb.moyeota.domain.user.service;

import com.ktb.moyeota.domain.auth.entity.OAuthAccount;
import com.ktb.moyeota.domain.auth.model.IssuedSession;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.domain.auth.repository.OAuthAccountRepository;
import com.ktb.moyeota.domain.auth.service.AuthSessionService;
import com.ktb.moyeota.domain.auth.store.SignupSessionStore;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.service.ChatParticipationService;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.repository.CompanionRepository;
import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.domain.image.service.ImagePromotionService;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.entity.UserAgreement;
import com.ktb.moyeota.domain.user.error.UserErrorCode;
import com.ktb.moyeota.domain.user.model.BankAccountCommand;
import com.ktb.moyeota.domain.user.model.MaskedBankAccount;
import com.ktb.moyeota.domain.user.model.MyProfile;
import com.ktb.moyeota.domain.user.model.RegisteredUser;
import com.ktb.moyeota.domain.user.model.SignupCommand;
import com.ktb.moyeota.domain.user.repository.UserAgreementRepository;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.external.kakao.KakaoUnlinkClient;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<CompanionStatus> ACTIVE_COMPANION_STATUSES =
            Set.of(CompanionStatus.RECRUITING, CompanionStatus.IN_PROGRESS);

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final AuthSessionService authSessionService;
    private final SignupSessionStore signupSessionStore;
    private final AccountNoCipher accountNoCipher;
    private final ImagePromotionService imagePromotionService;
    private final ImageUrlResolver imageUrlResolver;
    private final CompanionRepository companionRepository;
    private final TaxiPotParticipantRepository taxiPotParticipantRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final ChatParticipationService chatParticipationService;
    private final KakaoUnlinkClient kakaoUnlinkClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public RegisteredUser register(SignupSessionView signupSession, SignupCommand command) {
        if (userRepository.existsByNickname(command.nickname())) {
            throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATE);
        }

        String profileImageKey = promoteProfileImage(signupSession, command.profileImageKey());
        User user = createAccountInTransaction(signupSession, command, profileImageKey);
        IssuedSession session = authSessionService.issue(user.getId());
        closeSignupSession(signupSession);
        return new RegisteredUser(
                user.getId(), imageUrlResolver.toUrl(user.getProfileImageUrl()), user.getCreatedAt(), session);
    }

    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    @Transactional(readOnly = true)
    public MyProfile findMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return new MyProfile(user.getId(), user.getNickname(),
                imageUrlResolver.toUrl(user.getProfileImageUrl()), user.hasBankAccount());
    }

    @Transactional
    public MaskedBankAccount replaceBankAccount(Long userId, BankAccountCommand bankAccount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        registerBankAccount(user, bankAccount);
        return MaskedBankAccount.of(bankAccount.bankName(), bankAccount.accountNo());
    }

    public void withdraw(Long userId) {
        List<String> kakaoUserIds = transactionTemplate.execute(status -> withdrawAccount(userId));
        kakaoUserIds.forEach(kakaoUnlinkClient::unlink);
    }

    private List<String> withdrawAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        if (user.isWithdrawn()) {
            return List.of();
        }
        if (companionRepository.existsByHostIdAndStatusIn(userId, ACTIVE_COMPANION_STATUSES)) {
            throw new BusinessException(UserErrorCode.ACTIVE_HOST_EXISTS);
        }
        if (taxiPotParticipantRepository.existsCurrentTaxiPot(userId)) {
            throw new BusinessException(UserErrorCode.ACTIVE_TAXI_POT_EXISTS);
        }

        companionParticipantRepository.findJoinedCompanionPostIds(userId, ACTIVE_COMPANION_STATUSES)
                .forEach(companionId -> chatParticipationService.leave(userId, companionId));
        user.withdraw(LocalDateTime.now(clock));
        List<String> kakaoUserIds = oAuthAccountRepository.findByUserId(userId).stream()
                .map(OAuthAccount::getProviderUserId)
                .toList();
        oAuthAccountRepository.deleteByUserId(userId);
        authSessionService.revokeAll(userId);
        return kakaoUserIds;
    }

    private String promoteProfileImage(SignupSessionView signupSession, String tmpKey) {
        if (tmpKey == null) {
            return null;
        }
        return imagePromotionService.promote(
                UploadScope.signup(signupSession.tokenHash()), ImagePurpose.PROFILE, tmpKey);
    }

    private User createAccountInTransaction(
            SignupSessionView signupSession, SignupCommand command, String profileImageKey) {
        try {
            return transactionTemplate.execute(status -> createAccount(signupSession, command, profileImageKey));
        } catch (DataIntegrityViolationException e) {
            if (userRepository.existsByNickname(command.nickname())) {
                throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATE);
            }
            throw e;
        }
    }

    private User createAccount(SignupSessionView signupSession, SignupCommand command, String profileImageKey) {
        User user = User.register(signupSession.name(), command.nickname(), command.gender(), profileImageKey);
        registerBankAccount(user, command.bankAccount());
        userRepository.save(user);
        oAuthAccountRepository.save(
                OAuthAccount.link(user, signupSession.provider(), signupSession.providerUserId()));
        userAgreementRepository.save(
                UserAgreement.agree(user, command.agreements(), LocalDateTime.now(clock)));
        return user;
    }

    private void registerBankAccount(User user, BankAccountCommand bankAccount) {
        if (bankAccount != null) {
            user.registerBankAccount(bankAccount.bankName(), accountNoCipher.encrypt(bankAccount.accountNo()));
        }
    }

    private void closeSignupSession(SignupSessionView signupSession) {
        try {
            signupSessionStore.delete(signupSession.tokenHash());
        } catch (RuntimeException e) {
            log.warn("[SIGNUP_SESSION_CLEANUP_FAILED] provider={}", signupSession.provider(), e);
        }
    }
}
