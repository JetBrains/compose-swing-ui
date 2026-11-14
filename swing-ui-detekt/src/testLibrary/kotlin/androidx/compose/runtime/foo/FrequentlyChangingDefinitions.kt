/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.runtime.foo

import androidx.compose.runtime.annotation.FrequentlyChangingValue

/** Returns a frequently changing value. */
@FrequentlyChangingValue
public fun test(): Int = Foo().value

/** Holds a frequently changing value. */
public class Foo {
    /** The frequently changing value. */
    @get:FrequentlyChangingValue
    public var value: Int = 5
}

/** Exposes frequently changing values. */
public interface Bar {
    /** Calculates a frequently changing value. */
    @FrequentlyChangingValue
    public fun calculateValue(): Int

    /** The frequently changing value. */
    @get:FrequentlyChangingValue
    public var value: Int
}

/** Implements [Bar]. */
public class BarImpl : Bar {
    override fun calculateValue(): Int = 5

    override var value: Int = 5
}

/** Exposes a value without an annotation. */
public interface TopLevelWithoutAnnotation {
    /** The value. */
    public var value: Int
}

/** Adds the annotation to the inherited value. */
public abstract class InnerLevelWithAnnotation : TopLevelWithoutAnnotation {
    @get:FrequentlyChangingValue
    override var value: Int = 5

    /** A value supplied by subclasses. */
    public abstract val other: Int
}

/** Overrides the annotated value without repeating the annotation. */
public class LowerLevelWithoutAnnotation : InnerLevelWithAnnotation() {
    override var value: Int = 5

    override val other: Int = 5
}
