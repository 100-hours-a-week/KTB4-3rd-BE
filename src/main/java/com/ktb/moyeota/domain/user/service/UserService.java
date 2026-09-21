package com.ktb.moyeota.domain.user.service;

import com.ktb.moyeota.domain.auth.entity.OAuthAccount;
import com.ktb.moyeota.domain.auth.model.IssuedSession;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.domain.auth.repository.OAuthAccountRepository;
import com.ktb.moyeota.domain.auth.service.AuthSessionService;
import com.ktb.moyeota.domain.auth.store.SignupSessionStore;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.entity.UserAgreement;
import com.ktb.moyeota.domain.user.error.UserErrorCode;
import com.ktb.moyeota.domain.user.model.BankAccountCommand;
import com.ktb.moyeota.domain.user.model.RegisteredUser;
import com.ktb.moyeota.domain.user.model.SignupCommand;
import com.ktb.moyeota.domain.user.repository.UserAgreementRepository;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final AuthSessionService authSessionService;
    private final SignupSessionStore signupSessionStore;
    private final AccountNoCipher accountNoCipher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public RegisteredUser register(SignupSessionView signupSession, SignupCommand command) {
        if (userRepository.existsByNickname(command.nickname())) {
            throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATE);
        }

        User user = createAccountInTransaction(signupSession, command);
        IssuedSession session = authSessionService.issue(user.getId());
        closeSignupSession(signupSession);
        return new RegisteredUser(user.getId(), user.getProfileImageUrl(), user.getCreatedAt(), session);
    }

    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    private User createAccountInTransaction(SignupSessionView signupSession, SignupCommand command) {
        try {
            return transactionTemplate.execute(status -> createAccount(signupSession, command));
        } catch (DataIntegrityViolationException e) {
            if (userRepository.existsByNickname(command.nickname())) {
                throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATE);
            }
            throw e;
        }
    }

    private User createAccount(SignupSessionView signupSession, SignupCommand command) {
        User user = User.register(signupSession.name(), command.nickname(), command.gender(), null);
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
