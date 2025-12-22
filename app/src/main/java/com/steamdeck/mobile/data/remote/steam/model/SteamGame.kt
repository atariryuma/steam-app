package com.steamdeck.mobile.data.remote.steam.model

import com.google.gson.annotations.SerializedName

/**
 * Game information retrieved from Steam Web API
 */
data class SteamGame(
 @SerializedName("appid")
 val appId: Long,

 @SerializedName("name")
 val name: String,

 @SerializedName("playtime_forever")
 val playtimeMinutes: Long,

 @SerializedName("playtime_2weeks")
 val playtime2Weeks: Long? = null,

 @SerializedName("img_icon_url")
 val imgIconUrl: String,

 @SerializedName("img_logo_url")
 val imgLogoUrl: String,

 @SerializedName("has_community_visible_stats")
 val hasCommunityVisibleStats: Boolean? = null
) {
 /**
  * Generate icon URL
  */
 fun getIconUrl(): String {
  return "https://media.steampowered.com/steamcommunity/public/images/apps/$appId/$imgIconUrl.jpg"
 }

 /**
  * Generate logo URL
  */
 fun getLogoUrl(): String {
  return "https://media.steampowered.com/steamcommunity/public/images/apps/$appId/$imgLogoUrl.jpg"
 }

 /**
  * Generate header image URL (460x215)
  */
 fun getHeaderUrl(): String {
  return "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
 }

 /**
  * Library asset URL (600x900)
  */
 fun getLibraryAssetUrl(): String {
  return "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
 }
}

/**
 * Steam API GetOwnedGames response
 */
data class GetOwnedGamesResponse(
 @SerializedName("response")
 val response: OwnedGamesData
)

data class OwnedGamesData(
 @SerializedName("game_count")
 val gameCount: Int,

 @SerializedName("games")
 val games: List<SteamGame>
)

/**
 * Steam API GetPlayerSummaries response
 */
data class GetPlayerSummariesResponse(
 @SerializedName("response")
 val response: PlayerSummariesData
)

data class PlayerSummariesData(
 @SerializedName("players")
 val players: List<SteamPlayer>
)

data class SteamPlayer(
 @SerializedName("steamid")
 val steamId: String,

 @SerializedName("personaname")
 val personaName: String,

 @SerializedName("profileurl")
 val profileUrl: String,

 @SerializedName("avatar")
 val avatar: String,

 @SerializedName("avatarmedium")
 val avatarMedium: String,

 @SerializedName("avatarfull")
 val avatarFull: String,

 @SerializedName("timecreated")
 val timeCreated: Long? = null
)
