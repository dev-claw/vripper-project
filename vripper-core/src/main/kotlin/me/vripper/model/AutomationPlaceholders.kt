package me.vripper.model

data class AutomationPlaceholder(
    val key: String,
    val description: String,
)

object AutomationPlaceholders {
    val all: List<AutomationPlaceholder> = listOf(
        AutomationPlaceholder("post.id", "Internal post id"),
        AutomationPlaceholder("post.vgPostId", "ViperGirls post id"),
        AutomationPlaceholder("post.title", "Post title"),
        AutomationPlaceholder("post.url", "Post URL"),
        AutomationPlaceholder("post.forum", "Forum name"),
        AutomationPlaceholder("post.thread", "Thread title"),
        AutomationPlaceholder("post.threadId", "ViperGirls thread id"),
        AutomationPlaceholder("post.directory", "Parent download directory"),
        AutomationPlaceholder("post.folder", "Post folder name"),
        AutomationPlaceholder("post.path", "Actual artifact being processed: the zip when compression is enabled, otherwise the post folder (including the moved location)"),
        AutomationPlaceholder("post.hosts", "Comma-separated image hosts"),
        AutomationPlaceholder("post.host", "First image host"),
        AutomationPlaceholder("post.total", "Total number of images"),
        AutomationPlaceholder("post.done", "Number of downloaded images"),
        AutomationPlaceholder("post.progress", "Download progress from 0 to 100"),
        AutomationPlaceholder("post.addedOn", "Date and time the post was added"),
    )
}
