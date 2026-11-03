package cloud.elsa.app.feature.auth.di

import cloud.elsa.app.feature.auth.login.LoginApi
import cloud.elsa.app.feature.auth.login.LoginNoticeStore
import cloud.elsa.app.feature.auth.login.LoginRepository
import cloud.elsa.app.feature.auth.login.LoginViewModel
import cloud.elsa.app.feature.auth.login.RemoteLoginApi
import cloud.elsa.app.feature.auth.onboarding.OnboardingViewModel
import cloud.elsa.app.feature.auth.reset.PasswordResetApi
import cloud.elsa.app.feature.auth.reset.PasswordResetRepository
import cloud.elsa.app.feature.auth.reset.PasswordResetViewModel
import cloud.elsa.app.feature.auth.reset.RemotePasswordResetApi
import cloud.elsa.app.feature.auth.signup.SignupApi
import cloud.elsa.app.feature.auth.signup.SignupRepository
import cloud.elsa.app.feature.auth.signup.RemoteSignupApi
import cloud.elsa.app.feature.auth.signup.SignupViewModel
import cloud.elsa.app.feature.auth.social.RemoteSocialConsentApi
import cloud.elsa.app.feature.auth.social.RemoteSocialLoginApi
import cloud.elsa.app.feature.auth.social.SocialConsentApi
import cloud.elsa.app.feature.auth.social.SocialConsentRepository
import cloud.elsa.app.feature.auth.social.SocialConsentViewModel
import cloud.elsa.app.feature.auth.social.SocialLoginApi
import cloud.elsa.app.feature.auth.social.SocialLoginRepository
import cloud.elsa.app.feature.auth.splash.SplashViewModel
import cloud.elsa.app.feature.auth.terms.TermsViewModel
import org.koin.dsl.module

/** feature:auth 의 Koin 모듈. Api·Repository 는 single, ViewModel 은 factory. */
val authModule = module {

    // Splash (세션 복원)
    factory { SplashViewModel(sessionRepository = get(), settingStorage = get()) }

    // Onboarding (처음 실행 때 한 번)
    factory { OnboardingViewModel(settingStorage = get()) }

    // Login
    single<LoginApi> { RemoteLoginApi(baseApi = get()) }
    single { LoginRepository(loginApi = get(), loginStorage = get(), sessionRepository = get()) }
    single { LoginNoticeStore() }
    factory { LoginViewModel(loginRepository = get(), loginNotices = get(), socialLoginRepository = get()) }

    // 소셜 로그인 (DEC-105~109) — SDK 인증은 플랫폼, 서버 교환·세션은 여기
    single<SocialLoginApi> { RemoteSocialLoginApi(baseApi = get()) }
    single { SocialLoginRepository(api = get(), loginStorage = get(), sessionRepository = get()) }
    // 소셜 신규 가입의 약관 동의(18-4)
    single<SocialConsentApi> { RemoteSocialConsentApi(baseApi = get()) }
    single { SocialConsentRepository(api = get(), sessionRepository = get()) }
    factory { SocialConsentViewModel(repository = get()) }

    // Signup
    single<SignupApi> { RemoteSignupApi(baseApi = get()) }
    single { SignupRepository(signupApi = get()) }
    factory { (marketingAgreed: Boolean) ->
        SignupViewModel(marketingAgreed = marketingAgreed, signupRepository = get(), loginNotices = get())
    }

    // 약관 동의 (회원가입 전)
    factory { TermsViewModel() }

    // 비밀번호 재설정
    single<PasswordResetApi> { RemotePasswordResetApi(baseApi = get()) }
    single { PasswordResetRepository(api = get()) }
    factory { PasswordResetViewModel(repository = get(), loginNotices = get()) }
}
