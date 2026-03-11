package com.echain.app

import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.content.ComponentName
import android.content.Context

class MediaListenerService : NotificationListenerService() {

    companion object {
        var songTitle: String = "SONG NAME"
        var artistName: String = "ARTIST NAME"
        var albumName: String = "ALBUM TITLE"
        var onMediaChanged: (() -> Unit)? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        startMediaListener()
    }

    private fun startMediaListener() {
        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaListenerService::class.java)

        mediaSessionManager.addOnActiveSessionsChangedListener({ controllers ->
            controllers?.firstOrNull()?.registerCallback(object : android.media.session.MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    metadata?.let {
                        songTitle = it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "SONG NAME"
                        artistName = it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "ARTIST NAME"
                        albumName = it.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "ALBUM TITLE"
                        onMediaChanged?.invoke()
                    }
                }
            })
        }, componentName)
    }
}