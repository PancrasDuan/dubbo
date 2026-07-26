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
package org.apache.dubbo.sourcelearning.api;

public final class StackTraceFormatter {

    private static final int MAX_FRAMES = 80;

    private StackTraceFormatter() {}

    public static String captureDubboFrames() {
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (!frame.getClassName().startsWith("org.apache.dubbo")) {
                continue;
            }
            if (frame.getClassName().equals(StackTraceFormatter.class.getName())) {
                continue;
            }
            result.append("\n    at ").append(frame);
            if (++count == MAX_FRAMES) {
                result.append("\n    ... truncated after ").append(MAX_FRAMES).append(" Dubbo frames");
                break;
            }
        }
        return result.toString();
    }
}
