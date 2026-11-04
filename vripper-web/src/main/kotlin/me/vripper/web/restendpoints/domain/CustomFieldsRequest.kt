package me.vripper.web.restendpoints.domain

import me.vripper.entities.CustomField

data class CustomFieldsRequest(val postId: Long, val customFields: List<CustomField>)
