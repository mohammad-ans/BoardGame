package com.example.boardgame

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class Api(private val baseUrl: String) {
    private val client = OkHttpClient()

    fun fetchProfile(username: String, onResult: (ProfileResult?) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url("$baseUrl/profile/$username").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Check your network")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string()
                    if(body == null){
                        onError("Empty Response")
                        return
                    }
                    try{
                        val json = JSONObject(body)
                        if(json.has("error")) {
                            onError(json.getString("error"))
                            return
                        }
                        onResult(ProfileResult(localName = json.getString("local_name"), wins = json.getInt("wins"), losses = json.getInt("losses")))
                    }
                    catch (e: Exception) {
                        onError("${e.message}")
                    }
                }
            }
        })
    }
    fun leaderboard(onResult: (List<ProfileResult>) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url("$baseUrl/leaderboard").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Network Error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string()
                    if(body == null) {
                        onError("Empty Response")
                    }
                    try{
                        val json = JSONArray(body)
                        val entries = (0 until json.length()).map {
                            val obj = json.getJSONObject(it)
                            ProfileResult(localName = obj.getString("lcoal_name"), wins=obj.getInt("wins"), losses = obj.getInt("losses"))
                        }
                        onResult(entries)
                    }
                    catch (e: Exception) {
                        onError("${e.message}")
                    }
                }
            }
        })
    }
}

data class ProfileResult(val localName: String, val wins: Int, val losses: Int)