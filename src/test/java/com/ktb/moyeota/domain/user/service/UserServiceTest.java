package com.ktb.moyeota.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.auth.model.IssuedSession;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.domain.auth.repository.OAuthAccountRepository;
import com.ktb.moyeota.domain.auth.service.AuthSessionService;
import com.ktb.moyeota.domain.auth.store.SignupSessionStore;
import com.ktb.moyeota.domain.image.error.ImageErrorCode;
import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.domain.image.service.ImagePromotionService;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.entity.UserAgreement;
import com.ktb.moyeota.domain.user.error.UserErrorCode;
import com.ktb.moyeota.domain.user.model.AgreementsCommand;
import com.ktb.moyeota.domain.user.model.BankAccountCommand;
import com.ktb.moyeota.domain.user.model.RegisteredUser;
import com.ktb.moyeota.domain.user.model.SignupCommand;
import com.ktb.moyeota.domain.user.repository.UserAgreementRepository;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import com.ktb.moyeota.global.crypto.CryptoProperties;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.external.s3.S3Properties;
import com.ktb.moyeota.global.security.jwt.AccessToken;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T09:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final SignupCommand COMMAND =
            new SignupCommand("길동이", Gender.FEMALE, null, null, new AgreementsCommand(true, false));
    private static final String SIGNUP_HASH = "4e30b9ed47b5ea2e" + "0".repeat(48);
    private static final String TMP_KEY = "tmp/profile/s-4e30b9ed47b5ea2e/2aac932d-f68b-4491-9891-fd47671fcf02.jpg";
    private static final String FINAL_KEY = "profile/2aac932d-f68b-4491-9891-fd47671fcf02.jpg";
    private static final SignupCommand WITH_IMAGE =
            new SignupCommand("길동이", Gender.FEMALE, TMP_KEY, null, new AgreementsCommand(true, false));
    private static final IssuedSession ISSUED = new IssuedSession(
            new AccessToken("access-value", 1800), "refresh-value", Duration.ofDays(7));

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthAccountRepository oAuthAccountRepository;

    @Autowired
    private UserAgreementRepository userAgreementRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final AuthSessionService authSessionService = mock(AuthSessionService.class);
    private final SignupSessionStore signupSessionStore = mock(SignupSessionStore.class);
    private final AccountNoCipher accountNoCipher = new AccountNoCipher(new CryptoProperties(
            Base64.getEncoder().encodeToString(new byte[32])));
    private final ImagePromotionService imagePromotionService = mock(ImagePromotionService.class);
    private final ImageUrlResolver imageUrlResolver = new ImageUrlResolver(new S3Properties(
            "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test"));

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, oAuthAccountRepository, userAgreementRepository,
                authSessionService, signupSessionStore, accountNoCipher, imagePromotionService, imageUrlResolver,
                transactionTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));
        given(authSessionService.issue(any())).willReturn(ISSUED);
    }

    @Test
    @DisplayName("이름은 카카오 닉네임으로, 닉네임·성별은 본문 값으로 계정을 만든다")
    void createsUserWithKakaoNameAndRequestedNickname() {
        RegisteredUser registered = service.register(signupSession("카카오닉네임"), COMMAND);

        User user = userRepository.findById(registered.userId()).orElseThrow();
        assertThat(user.getName()).isEqualTo("카카오닉네임");
        assertThat(user.getNickname()).isEqualTo("길동이");
        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(user.getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("계좌를 입력하지 않으면 은행명과 계좌번호는 비어 있다")
    void bankAccountIsOptional() {
        RegisteredUser registered = service.register(signupSession("카카오닉네임"), COMMAND);

        User user = userRepository.findById(registered.userId()).orElseThrow();
        assertThat(user.getBankName()).isNull();
        assertThat(user.getAccountNo()).isNull();
    }

    @Test
    @DisplayName("계좌를 입력하면 은행명은 그대로, 계좌번호는 암호화해 저장한다")
    void storesEncryptedAccountNo() {
        SignupCommand withAccount = new SignupCommand("길동이", Gender.FEMALE, null,
                new BankAccountCommand("shinhan", "11012345678"), new AgreementsCommand(false, false));

        RegisteredUser registered = service.register(signupSession("카카오닉네임"), withAccount);

        User user = userRepository.findById(registered.userId()).orElseThrow();
        assertThat(user.getBankName()).isEqualTo("shinhan");
        assertThat(new String(user.getAccountNo(), StandardCharsets.ISO_8859_1)).doesNotContain("11012345678");
        assertThat(accountNoCipher.decrypt(user.getAccountNo())).isEqualTo("11012345678");
    }

    @Test
    @DisplayName("계좌정보 제3자 제공에 동의하지 않아도 계좌는 저장된다")
    void accountIsStoredRegardlessOfThirdPartyAgreement() {
        SignupCommand declined = new SignupCommand("길동이", Gender.FEMALE, null,
                new BankAccountCommand("shinhan", "11012345678"), new AgreementsCommand(false, false));

        RegisteredUser registered = service.register(signupSession("카카오닉네임"), declined);

        assertThat(userRepository.findById(registered.userId()).orElseThrow().getBankName()).isEqualTo("shinhan");
        assertThat(userAgreementRepository.findAll().getFirst().isAccountThirdPartyAgreed()).isFalse();
    }

    @Test
    @DisplayName("회원가입 세션의 카카오 회원번호를 새 계정에 연결한다")
    void linksKakaoAccount() {
        RegisteredUser registered = service.register(signupSession("카카오닉네임"), COMMAND);

        assertThat(oAuthAccountRepository.findActiveUserId(OAuthProvider.KAKAO, "1234567890"))
                .contains(registered.userId());
    }

    @Test
    @DisplayName("약관 동의를 가입 시각으로 기록한다")
    void recordsAgreements() {
        service.register(signupSession("카카오닉네임"), COMMAND);

        UserAgreement agreement = userAgreementRepository.findAll().getFirst();
        assertThat(agreement.getRequiredAgreedAt()).isEqualTo(NOW_LOCAL);
        assertThat(agreement.isAccountThirdPartyAgreed()).isTrue();
        assertThat(agreement.isMarketingAgreed()).isFalse();
        assertThat(agreement.getMarketingChangedAt()).isEqualTo(NOW_LOCAL);
    }

    @Test
    @DisplayName("가입 직후 그 계정으로 로그인 세션을 발급하고 응답에 쓸 값을 돌려준다")
    void issuesLoginSession() {
        RegisteredUser registered = service.register(signupSession("카카오닉네임"), COMMAND);

        verify(authSessionService).issue(registered.userId());
        assertThat(registered.session()).isEqualTo(ISSUED);
        assertThat(registered.profileImageUrl()).isNull();
        assertThat(registered.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("가입이 끝나면 회원가입 세션을 지운다")
    void closesSignupSession() {
        service.register(signupSession("카카오닉네임"), COMMAND);

        verify(signupSessionStore).delete(SIGNUP_HASH);
    }

    @Test
    @DisplayName("회원가입 세션 정리가 실패해도 가입은 성공이다")
    void cleanupFailureDoesNotFailSignup() {
        willThrow(new IllegalStateException("store down")).given(signupSessionStore).delete(any());

        RegisteredUser registered = service.register(signupSession("카카오닉네임"), COMMAND);

        assertThat(userRepository.findById(registered.userId())).isPresent();
    }

    @Test
    @DisplayName("닉네임이 이미 쓰이고 있으면 NICKNAME_DUPLICATE이고 아무것도 만들지 않는다")
    void duplicateNickname() {
        userRepository.saveAndFlush(User.register("김철수", "길동이", Gender.MALE, null));

        assertThatThrownBy(() -> service.register(signupSession("카카오닉네임"), COMMAND))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.NICKNAME_DUPLICATE));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(oAuthAccountRepository.count()).isZero();
        assertThat(userAgreementRepository.count()).isZero();
        verify(authSessionService, never()).issue(any());
        verify(signupSessionStore, never()).delete(any());
    }

    @Test
    @DisplayName("닉네임이 이미 쓰이고 있으면 프로필 이미지를 확정하지 않는다")
    void duplicateNicknameSkipsImagePromotion() {
        userRepository.saveAndFlush(User.register("김철수", "길동이", Gender.MALE, null));

        assertThatThrownBy(() -> service.register(signupSession("카카오닉네임"), WITH_IMAGE))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(imagePromotionService);
    }

    @Test
    @DisplayName("프로필 이미지 키가 없으면 S3를 부르지 않고 이미지 없이 가입한다")
    void signsUpWithoutImage() {
        RegisteredUser registered = service.register(signupSession("카카오닉네임"), COMMAND);

        assertThat(registered.profileImageUrl()).isNull();
        verifyNoInteractions(imagePromotionService);
    }

    @Test
    @DisplayName("프로필 이미지 키는 현재 회원가입 세션의 범위로 확정하고, 확정된 키를 저장하고 URL을 돌려준다")
    void promotesProfileImageWithSignupScope() {
        given(imagePromotionService.promote(any(), any(), any())).willReturn(FINAL_KEY);

        RegisteredUser registered = service.register(signupSession("카카오닉네임"), WITH_IMAGE);

        verify(imagePromotionService).promote(UploadScope.signup(SIGNUP_HASH), ImagePurpose.PROFILE, TMP_KEY);
        assertThat(userRepository.findById(registered.userId()).orElseThrow().getProfileImageUrl())
                .isEqualTo(FINAL_KEY);
        assertThat(registered.profileImageUrl()).isEqualTo("https://cdn.moyeota.test/" + FINAL_KEY);
    }

    @Test
    @DisplayName("프로필 이미지를 확정하지 못하면 계정을 만들지 않는다")
    void imagePromotionFailureCreatesNothing() {
        given(imagePromotionService.promote(any(), any(), any()))
                .willThrow(new BusinessException(ImageErrorCode.IMAGE_NOT_EXISTS));

        assertThatThrownBy(() -> service.register(signupSession("카카오닉네임"), WITH_IMAGE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImageErrorCode.IMAGE_NOT_EXISTS));

        assertThat(userRepository.count()).isZero();
        assertThat(oAuthAccountRepository.count()).isZero();
        assertThat(userAgreementRepository.count()).isZero();
        verify(authSessionService, never()).issue(any());
        verify(signupSessionStore, never()).delete(any());
    }

    @Test
    @DisplayName("아무도 쓰지 않는 닉네임은 사용할 수 있다")
    void unusedNicknameIsAvailable() {
        assertThat(service.isNicknameAvailable("길동이")).isTrue();
    }

    @Test
    @DisplayName("이미 쓰이고 있는 닉네임은 사용할 수 없다")
    void usedNicknameIsNotAvailable() {
        userRepository.saveAndFlush(User.register("김철수", "길동이", Gender.MALE, null));

        assertThat(service.isNicknameAvailable("길동이")).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 사용자가 쓰던 닉네임은 다시 사용할 수 있다")
    void withdrawnUsersNicknameIsAvailable() {
        User user = userRepository.saveAndFlush(User.register("김철수", "길동이", Gender.MALE, null));
        user.withdraw(NOW_LOCAL);
        userRepository.saveAndFlush(user);

        assertThat(service.isNicknameAvailable("길동이")).isTrue();
    }

    private SignupSessionView signupSession(String kakaoName) {
        return new SignupSessionView(
                SIGNUP_HASH, OAuthProvider.KAKAO, "1234567890", kakaoName, LocalDateTime.of(2026, 1, 1, 9, 15));
    }
}
