/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Option;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/**
 * This recipe will find any method invocations that match the method pattern, ensure the method is statically imported
 * and convert's the invocation to use the static import by dropping the invocation's select.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class UseStaticImport extends Recipe {

    /**
     * A method pattern, expressed as a pointcut expression, that is used to find matching method invocations.
     * See {@link  MethodMatcher} for details on the expression's syntax.
     */
    @Option(displayName = "Method pattern", description = "A method pattern, expressed as a pointcut expression, that is used to find matching method invocations.")
    String methodPattern;

    @Override
    public String getDisplayName() {
        return "Use static import";
    }

    @Override
    public String getDescription() {
        return "Replace non-static method calls with static ones";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UseStaticImportVisitor(new MethodMatcher(methodPattern));
    }

    private static class UseStaticImportVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher methodMatcher;

        private UseStaticImportVisitor(MethodMatcher methodMatcher) {
            this.methodMatcher = methodMatcher;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            if (methodMatcher.matches(m) && m.getSelect() != null) {
                if (m.getType() != null) {
                    JavaType.FullyQualified receiverType = m.getType().getDeclaringType();
                    maybeRemoveImport(receiverType);

                    AddImport<ExecutionContext> addStatic = new AddImport<>(
                            receiverType.getFullyQualifiedName(),
                            m.getSimpleName(),
                            false);
                    if (!getAfterVisit().contains(addStatic)) {
                        doAfterVisit(addStatic);
                    }
                }

                m = m.withSelect(null).withName(m.getName().withPrefix(m.getSelect().getPrefix()));
            }
            return m;
        }
    }
}
