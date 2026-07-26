/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.sourcelearning.consumer;

import org.apache.dubbo.rpc.BaseFilter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.Directory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

final class InvokerTreeFormatter {

    private static final Set<String> LINK_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "directory", "filterInvoker", "interceptorInvoker", "invoker", "nextNode", "originalInvoker")));

    private InvokerTreeFormatter() {}

    static String format(Invoker<?> root) {
        StringBuilder result = new StringBuilder();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        append(result, "root", root, 0, visited);
        return result.toString();
    }

    private static void append(StringBuilder result, String role, Object node, int depth, Set<Object> visited) {
        result.append(System.lineSeparator());
        appendIndent(result, depth + 1);
        result.append(role).append(" -> ").append(node.getClass().getName());
        if (node instanceof Invoker<?>) {
            result.append(" [")
                    .append(((Invoker<?>) node).getUrl().getAddress())
                    .append(']');
        }
        if (!visited.add(node)) {
            result.append(" (same instance)");
            return;
        }

        if (node instanceof Directory<?>) {
            int index = 0;
            for (Invoker<?> candidate : ((Directory<?>) node).getAllInvokers()) {
                append(result, "candidate[" + index++ + "]", candidate, depth + 1, visited);
            }
        }

        for (Class<?> type = node.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if ("filter".equals(field.getName())) {
                    Object filter = read(field, node);
                    if (filter instanceof BaseFilter) {
                        result.append(System.lineSeparator());
                        appendIndent(result, depth + 2);
                        result.append("filter -> ").append(filter.getClass().getName());
                    }
                } else if (LINK_FIELDS.contains(field.getName())) {
                    Object child = read(field, node);
                    if (child instanceof Invoker<?> || child instanceof Directory<?>) {
                        append(result, field.getName(), child, depth + 1, visited);
                    }
                }
            }
        }
    }

    private static void appendIndent(StringBuilder result, int depth) {
        for (int index = 0; index < depth; index++) {
            result.append("  ");
        }
    }

    private static Object read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
