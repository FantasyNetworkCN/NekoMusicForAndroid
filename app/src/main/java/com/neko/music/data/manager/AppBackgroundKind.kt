package com.neko.music.data.manager

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.neko.music.R
import com.neko.music.config.AppConfig

/** 应用内可单独更换的壁纸位。 */
enum class AppBackgroundKind(
  @DrawableRes val defaultResId: Int,
  val fileName: String,
  val prefEnabledKey: String,
  @StringRes val titleResId: Int,
) {
  SubPage(
    defaultResId = R.drawable.playlist_background,
    fileName = "custom_bg_sub_page",
    prefEnabledKey = AppConfig.PrefConfig.KEY_CUSTOM_BG_SUB_PAGE,
    titleResId = R.string.personalization_background_sub_page,
  ),
  Home(
    defaultResId = R.drawable.home_background,
    fileName = "custom_bg_home",
    prefEnabledKey = AppConfig.PrefConfig.KEY_CUSTOM_BG_HOME,
    titleResId = R.string.personalization_background_home,
  ),
  MineHeader(
    defaultResId = R.drawable.background,
    fileName = "custom_bg_mine_header",
    prefEnabledKey = AppConfig.PrefConfig.KEY_CUSTOM_BG_MINE_HEADER,
    titleResId = R.string.personalization_background_mine_header,
  ),
  MyPlaylists(
    defaultResId = R.drawable.list_background,
    fileName = "custom_bg_my_playlists",
    prefEnabledKey = AppConfig.PrefConfig.KEY_CUSTOM_BG_MY_PLAYLISTS,
    titleResId = R.string.personalization_background_my_playlists,
  ),
}
