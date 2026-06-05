package com.example.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MusicActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = MusicService.instance ?: return
        when (intent.action) {
            MusicService.ACTION_TOGGLE -> {
                service.togglePlayPause()
            }
            MusicService.ACTION_NEXT -> {
                // Next control handled inside Main or ViewModel callbacks or simply by starting next track in ViewModel
                val broadcastIntent = Intent("com.example.player.ACTION_NEXT")
                context.sendBroadcast(broadcastIntent)
            }
            MusicService.ACTION_PREV -> {
                // Prev control handled via broadcasts
                val broadcastIntent = Intent("com.example.player.ACTION_PREV")
                context.sendBroadcast(broadcastIntent)
            }
        }
    }
}
