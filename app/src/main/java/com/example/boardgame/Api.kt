package com.example.boardgame

import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private val mainHandler = Handler(Looper.getMainLooper())

    fun postResult(action: () -> Unit) {
        mainHandler.post(action)
    }

    fun fetchProfile(username: String, onResult: (ProfileResult?) -> Unit, onError: (String) -> Unit) : Call {
        val request = Request.Builder().url("$baseUrl/profile/$username").build()
        val call = client.newCall(request)
        call.enqueue(object : Callback {
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
        return call
    }
    fun leaderboard(onResult: (List<ProfileResult>) -> Unit, onError: (String) -> Unit): Call {
        val request = Request.Builder().url("$baseUrl/leaderboard").build()
        val call =  client.newCall(request)
        call.enqueue(object : Callback {
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
                            ProfileResult(localName = obj.getString("local_name"), wins=obj.getInt("wins"), losses = obj.getInt("losses"))
                        }
                        Log.e("leaderboard", "$entries")
                        onResult(entries)

                    }
                    catch (e: Exception) {
                        onError("${e.message}")
                    }
                }
            }
        })
        return call
    }
}

data class ProfileResult(val localName: String, val wins: Int, val losses: Int)