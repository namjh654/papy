package io.github.namjh654.papy.feature.auth.di

import io.github.namjh654.papy.feature.auth.login.LoginApi
import io.github.namjh654.papy.feature.auth.login.LoginNoticeStore
import io.github.namjh654.papy.feature.auth.login.LoginRepository
import io.github.namjh654.papy.feature.auth.login.LoginViewModel
import io.github.namjh654.papy.feature.auth.login.RemoteLoginApi
import io.github.namjh654.papy.feature.auth.onboarding.OnboardingViewModel
import io.github.namjh654.papy.feature.auth.reset.PasswordResetApi
import io.github.namjh654.papy.feature.auth.reset.PasswordResetRepository
import io.github.namjh654.papy.feature.auth.reset.PasswordResetViewModel
import io.github.namjh654.papy.feature.auth.reset.RemotePasswordResetApi
import io.github.namjh654.papy.feature.auth.signup.SignupApi
import io.github.namjh654.papy.feature.auth.signup.SignupRepository
import io.github.namjh654.papy.feature.auth.signup.RemoteSignupApi
import io.github.namjh654.papy.feature.auth.signup.SignupViewModel
import io.github.namjh654.papy.feature.auth.social.RemoteSocialConsentApi
import io.github.namjh654.papy.feature.auth.social.RemoteSocialLoginApi
import io.github.namjh654.papy.feature.auth.social.SocialConsentApi
import io.github.namjh654.papy.feature.auth.social.SocialConsentRepository
import io.github.namjh654.papy.feature.auth.social.SocialConsentViewModel
import io.github.namjh654.papy.feature.auth.social.SocialLoginApi
import io.github.namjh654.papy.feature.auth.social.SocialLoginRepository
import io.github.namjh654.papy.feature.auth.splash.SplashViewModel
import io.github.namjh654.papy.feature.auth.terms.TermsViewModel
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
