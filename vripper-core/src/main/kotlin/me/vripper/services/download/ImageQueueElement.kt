package me.vripper.services.download

internal data class ImageQueueElement(val imageEntityId: Long, val postEntityId: Long, val host: Byte)