package me.vripper.services

import kotlinx.coroutines.flow.Flow
import me.vripper.model.Image
import me.vripper.model.Metadata
import me.vripper.model.Post

interface IPostEventEndpoint {
    fun onNewPosts(): Flow<Post>
    fun onUpdatePosts(): Flow<Post>
    fun onDeletePosts(): Flow<Long>
    fun onUpdateMetadata(): Flow<Metadata>
    fun onUpdateImagesByPostEntityId(postEntityId: Long): Flow<Image>
    fun onUpdateImages(): Flow<Image>
    fun onStopped(): Flow<Long>
}
