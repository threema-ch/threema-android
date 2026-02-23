package ch.threema.domain.types

/**
 * String representation of [ch.threema.domain.models.MessageId], i.e., a chat protocol message identifier assigned by the sender.
 * [ch.threema.domain.models.MessageId] should be used instead of this if possible, as we might want to get rid of the string representation
 * in the future.
 */
typealias MessageIdString = String
