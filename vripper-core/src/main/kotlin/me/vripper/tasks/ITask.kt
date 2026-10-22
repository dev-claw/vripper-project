package me.vripper.tasks

interface ITask {
    suspend fun run()
    fun getName(): String
    fun getCategory(): TaskCategory
    fun getDescription(): String
    fun getResult(): String
    fun getStatus(): TaskStatus
}
