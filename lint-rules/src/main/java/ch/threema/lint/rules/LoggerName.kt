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

package ch.threema.lint.rules

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.getIoFile

object LoggerName {
    val ISSUE = Issue.create(
        id = "LoggerName",
        briefDescription = "Logger name does not match class or file name",
        explanation = "The name passed into `getThreemaLogger` should match the name of the class or file the logger belongs to, " +
            "or be a period-separated suffix thereof.",
        category = Category.CUSTOM_LINT_CHECKS,
        priority = 8,
        severity = Severity.ERROR,
        implementation = Implementation(
            LoggerNameDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        ),
    )

    class LoggerNameDetector : Detector(), Detector.UastScanner {
        override fun getApplicableUastTypes(): List<Class<out UElement>> =
            listOf(UCallExpression::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler =
            LoggerNameVisitor(context)
    }

    class LoggerNameVisitor(private val context: JavaContext) : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            if (node.methodName != "getThreemaLogger") {
                return
            }

            val argument = node.getArgumentForParameter(0) ?: return
            val value = argument.evaluate() as? String ?: return

            val containingClassName = (
                node.getContainingUClass()
                    ?.name
                    ?: node.getContainingUClass()?.superTypes?.last()?.name
                )
                ?.removeSuffix("Kt")
            val sourceFileName = node.getContainingUFile()
                ?.getIoFile()
                ?.name
                ?.removeSuffix(".java")
                ?.removeSuffix(".kt")
                ?: return
            val expectedName = containingClassName ?: sourceFileName

            if (value != expectedName && !value.endsWith(".$expectedName")) {
                reportIssue(node)
            }
        }

        private fun reportIssue(node: UCallExpression) {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(node),
                message = "A logger's name should match that of the class or file it is defined in.",
            )
        }
    }
}
