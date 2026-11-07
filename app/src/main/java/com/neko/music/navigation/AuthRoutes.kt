package com.neko.music.navigation

/** 登录 / 注册 / 忘记密码：独立 NavHost 全屏页，不再叠在「我的」等页面上。 */
object AuthRoutes {
    const val LOGIN = "auth/login"

    fun isAuthRoute(route: String?): Boolean = route?.startsWith("auth/") == true
}
