/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package utils

import java.io.File

/**
 * Return the git hash, if git is installed.
 */
fun getGitHash(): String =
    runGitCommand("rev-parse", "--short", "HEAD")
        ?.ifEmpty { "?" }
        ?: "?"

/**
 * Return the latest available domain version from git, if git is installed.
 */
fun getGitVersion(): String? {
    val domainTagPrefix = "domain-v"
    val output = runGitCommand("describe", "--tags", "--match", "$domainTagPrefix*")
        ?: return null
    val regex = "^$domainTagPrefix([0-9.]+).*\$".toRegex()
    return regex.find(output)
        ?.groups
        ?.get(1)
        ?.value
}

/**
 * Return the name of the current git branch, if git is installed and the app is built from a git repository.
 * Otherwise, an empty string is returned.
 */
fun getGitBranch(): String? =
    runGitCommand("rev-parse", "--abbrev-ref", "HEAD")
        .orEmpty()

private fun runGitCommand(vararg args:  String): String? {
    val outputFile = File.createTempFile("git-command", "")
    try {
        val processBuilder = ProcessBuilder(listOf("git") + args.toList())
        processBuilder.redirectOutput(outputFile)
        val process = processBuilder.start()
        process.waitFor()
        return outputFile.readText().trim()
    } catch (_: Exception) {
        return null
    } finally {
        outputFile.delete()
    }
}
