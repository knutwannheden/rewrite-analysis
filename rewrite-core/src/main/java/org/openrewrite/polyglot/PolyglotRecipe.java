/*
 *  Copyright 2021 the original author or authors.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  https://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openrewrite.polyglot;

import lombok.experimental.NonFinal;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;

import static org.graalvm.polyglot.Value.asValue;
import static org.openrewrite.polyglot.PolyglotUtils.*;

public class PolyglotRecipe extends Recipe {

    private final String name;
    private final Value options;
    private final Value constructor;

    @Nullable
    @NonFinal
    private volatile Value instance;

    public PolyglotRecipe(String name, Value options, Value constructor) {
        this.name = name;
        this.options = options;
        this.constructor = constructor;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return invokeMemberOrElse(getInstance(), "getDisplayName", asValue(options.getMember("displayName")))
                .asString();
    }

    @Override
    public String getDescription() {
        return invokeMemberOrElse(getInstance(), "getDescription", asValue(options.getMember("description")))
                .asString();
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return invokeMember(getInstance(), "getVisitor")
                .map(v -> new PolyglotVisitor<>(v.getMember("visit"), super.getVisitor()))
                .orElseThrow(() -> new IllegalStateException("Must provide a visit function"));
    }

    private synchronized Value getInstance() {
        if (instance == null) {
            instance = jsExtend(constructor, "OpenRewrite.Recipe", "doNext", new DoNextProxy())
                    .map(v -> v.newInstance(options))
                    .orElseThrow(IllegalStateException::new);
        }
        return instance;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PolyglotRecipe)) {
            return false;
        }
        return ((PolyglotRecipe) o).getName().equals(getName());
    }

    private class DoNextProxy implements ProxyExecutable {
        @Override
        public Object execute(Value... arguments) {
            return doNext(arguments[0].as(Recipe.class));
        }
    }
}
