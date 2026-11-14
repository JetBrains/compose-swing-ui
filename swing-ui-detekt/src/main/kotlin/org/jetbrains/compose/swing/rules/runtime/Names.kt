/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/** Contains common names used for lint checks. */
internal object Names {
    object Runtime {
        val PackageName = packageName("androidx.compose.runtime")

        val Composable = Name(PackageName, "Composable")
        val CompositionLocal = Name(PackageName, "CompositionLocal")
        val State = Name(PackageName, "State")
        val MutableStateOf = Name(PackageName, "mutableStateOf")
        val StructuralEqualityPolicy = Name(PackageName, "structuralEqualityPolicy")

        object Annotation {
            val PackageName = packageName(Runtime.PackageName, "annotation")
            val FrequentlyChangingValue = Name(PackageName, "FrequentlyChangingValue")
            val RememberInComposition = Name(PackageName, "RememberInComposition")
        }
    }
}

/**
 * Represents a qualified package.
 *
 * @property segments the segments representing the package.
 */
internal class PackageName internal constructor(
    internal val segments: List<String>,
) {
    /** The Java-style package name for this [Name], separated with `.`. */
    val javaPackageName: String
        get() = segments.joinToString(".")
}

/** Represents the qualified name for an element. */
internal class Name
    internal constructor(
        private val pkg: PackageName,
        private val nameSegments: List<String>,
    ) {
        /** The short name for this [Name]. */
        val shortName: String
            get() = nameSegments.last()

        /** The [ClassId] for use with analysis APIs. */
        val classId: ClassId
            get() =
                ClassId(
                    FqName(pkg.segments.joinToString(".")),
                    org.jetbrains.kotlin.name.Name
                        .identifier(nameSegments.joinToString(".")),
                )

        /** The [PackageName] of this element. */
        val packageName: PackageName
            get() = pkg
    }

/** @return a [PackageName] with a Java-style (separated with `.`) [packageName]. */
internal fun packageName(packageName: String): PackageName = PackageName(packageName.split("."))

/** @return a [PackageName] with a Java-style (separated with `.`) [packageName]. */
internal fun packageName(
    packageName: PackageName,
    shortName: String,
): PackageName = PackageName(packageName.segments + shortName.split("."))

/** @return a [Name] with the provided [pkg] and Java-style (separated with `.`) [shortName]. */
internal fun Name(
    pkg: PackageName,
    shortName: String,
): Name = Name(pkg, shortName.split("."))
