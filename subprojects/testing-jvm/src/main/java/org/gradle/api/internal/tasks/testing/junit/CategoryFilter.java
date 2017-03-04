/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit;

import org.junit.experimental.categories.Category;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * This filter is used for filtering classes and methods that are annotated with the @Category annotation.
 *
 */
class CategoryFilter extends Filter {
    private final Set<Class<?>> included;
    private final Set<Class<?>> excluded;

    public CategoryFilter(final Set<Class<?>> inclusions, final Set<Class<?>> exclusions) {
        this.included = inclusions;
        this.excluded = exclusions;
    }

    /**
     * @see #toString()
     */
    @Override
    public String describe() {
        return toString();
    }

    /**
     * Returns string in the form <tt>&quot;[included categories] - [excluded categories]&quot;</tt>, where both
     * sets have comma separated names of categories.
     *
     * @return string representation for the relative complement of excluded categories set
     * in the set of included categories. Examples:
     * <ul>
     *  <li> <tt>&quot;categories [all]&quot;</tt> for all included categories and no excluded ones;
     *  <li> <tt>&quot;categories [all] - [A, B]&quot;</tt> for all included categories and given excluded ones;
     *  <li> <tt>&quot;categories [A, B] - [C, D]&quot;</tt> for given included categories and given excluded ones.
     * </ul>
     * @see Class#toString() name of category
     */
    @Override public String toString() {
        StringBuilder description= new StringBuilder("categories ")
            .append(included.isEmpty() ? "[all]" : included);
        if (!excluded.isEmpty()) {
            description.append(" - ").append(excluded);
        }
        return description.toString();
    }

    @Override
    public boolean shouldRun(Description description) {
        if (hasCorrectCategoryAnnotation(description)) {
            return true;
        }

        for (Description each : description.getChildren()) {
            if (shouldRun(each)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCorrectCategoryAnnotation(Description description) {
        final Set<Class<?>> childCategories = categories(description);

        // If a child has no categories, immediately return.
        if (childCategories.isEmpty()) {
            return included.isEmpty();
        }

        if (!excluded.isEmpty()) {
            if (matchesAnyParentCategories(childCategories, excluded)) {
                return false;
            }
        }

        // Couldn't be excluded, and with no suite's included categories treated as should run.
        return included.isEmpty() || matchesAnyParentCategories(childCategories, included);
    }

    /**
     * @return <tt>true</tt> if at least one (any) parent category match a child, otherwise <tt>false</tt>.
     * If empty <tt>parentCategories</tt>, returns <tt>false</tt>.
     */
    private boolean matchesAnyParentCategories(Set<Class<?>> childCategories, Set<Class<?>> parentCategories) {
        for (Class<?> parentCategory : parentCategories) {
            if (hasAssignableTo(childCategories, parentCategory)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Class<?>> categories(Description description) {
        Set<Class<?>> categories= new HashSet<Class<?>>();
        Collections.addAll(categories, directCategories(description));
        Collections.addAll(categories, directCategories(parentDescription(description)));
        return categories;
    }

    private static Description parentDescription(Description description) {
        Class<?> testClass= description.getTestClass();
        return testClass == null ? null : Description.createSuiteDescription(testClass);
    }

    private static Class<?>[] directCategories(Description description) {
        if (description == null) {
            return new Class<?>[0];
        }

        Category annotation= description.getAnnotation(Category.class);
        return annotation == null ? new Class<?>[0] : annotation.value();
    }

    private static boolean hasAssignableTo(Set<Class<?>> assigns, Class<?> to) {
        for (final Class<?> from : assigns) {
            if (to.isAssignableFrom(from)) {
                return true;
            }
        }
        return false;
    }
}
