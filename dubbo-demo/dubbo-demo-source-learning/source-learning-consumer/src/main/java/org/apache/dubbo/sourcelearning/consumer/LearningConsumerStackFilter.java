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

import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.sourcelearning.api.LearningRequest;
import org.apache.dubbo.sourcelearning.api.StackTraceFormatter;

import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LearningConsumerStackFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LearningConsumerStackFilter.class);
    private static final AtomicBoolean STACK_RECORDED = new AtomicBoolean();

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        logger.info(
                "LEARNING_CONSUMER_SELECTED requestId={} method={} target={} invokerType={} service={} invokeMode={} attachments={}",
                requestId(invocation),
                invocation.getMethodName(),
                invoker.getUrl().getAddress(),
                invoker.getClass().getName(),
                invocation.getTargetServiceUniqueName(),
                invocation instanceof RpcInvocation ? ((RpcInvocation) invocation).getInvokeMode() : "unknown",
                new TreeSet<>(invocation.getObjectAttachments().keySet()));
        if (STACK_RECORDED.compareAndSet(false, true)) {
            logger.info(
                    "LEARNING_CONSUMER_STACK requestId={} target={} thread={}{}",
                    requestId(invocation),
                    invoker.getUrl().getAddress(),
                    Thread.currentThread().getName(),
                    StackTraceFormatter.captureDubboFrames());
        }
        return invoker.invoke(invocation);
    }

    private String requestId(Invocation invocation) {
        Object[] arguments = invocation.getArguments();
        if (arguments.length > 0 && arguments[0] instanceof LearningRequest) {
            return ((LearningRequest) arguments[0]).getRequestId();
        }
        return "unknown";
    }
}
