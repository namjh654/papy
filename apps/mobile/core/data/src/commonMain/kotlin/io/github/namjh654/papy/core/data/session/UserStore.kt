package io.github.namjh654.papy.core.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 현재 로그인 사용자의 메모리 캐시입니다. 영속화는 UserStorage가 담당합니다. */
class UserStore {

    private val _user = MutableStateFlow<UserModel?>(null)
    val user: StateFlow<UserModel?> = _user.asStateFlow()

    fun setUser(user: UserModel) {
        _user.value = user
    }

    fun clear() {
        _user.value = null
    }
}
