package me.vripper.services

import me.vripper.model.Image
import me.vripper.model.Post

interface IPostQueryEndpoint {
    suspend fun findPost(postEntityId: Long): Post
    suspend fun findAllPosts(): List<Post>
    suspend fun findImagesByPostEntityId(postEntityId: Long): List<Image>
}
