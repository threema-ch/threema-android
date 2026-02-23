package ch.threema.data.repositories

/**
 * A token that can only be obtained by a model repository.
 *
 * The goal is to ensure that certain methods cannot be called by code outside the repository.
 *
 * Note: Should only be implemented by classes in model repositories!
 */
internal interface RepositoryToken
