package com.example.recemotion.domain.model

sealed class ConversationUpdateEvent {
    data class Analyzing(val message: String) : ConversationUpdateEvent()
    data class Done(val topicId: Long, val isNewTopic: Boolean, val entryId: Long) : ConversationUpdateEvent()
    data class Error(val message: String) : ConversationUpdateEvent()
}
