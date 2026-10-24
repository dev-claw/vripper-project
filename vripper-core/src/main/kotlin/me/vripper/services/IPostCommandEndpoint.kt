package me.vripper.services

interface IPostCommandEndpoint {

    enum class ScanOrigin(val str: String) {
        GENERIC("Generic"),
        PASTED("Pasted"),
        CLIPBOARD("Clipboard"),
        DROPPED("Dropped"),
    }

    suspend fun scanLinks(postLinks: String, origin: ScanOrigin = ScanOrigin.GENERIC)
    suspend fun restartAll(postEntityIds: List<Long> = listOf())
    suspend fun remove(postEntityIds: List<Long>)
    suspend fun stopAll(postEntityIds: List<Long> = emptyList())
    suspend fun clearCompleted(): List<Long>
    suspend fun rename(postEntityId: Long, newName: String)
    suspend fun renameToFirst(postEntityIds: List<Long>)
}
