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
package org.apache.dubbo.sourcelearning.provider;

import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.sourcelearning.api.LearningRequest;
import org.apache.dubbo.sourcelearning.api.LearningResponse;
import org.apache.dubbo.sourcelearning.api.LearningService;
import org.apache.dubbo.sourcelearning.api.StackTraceFormatter;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

@DubboService(protocol = "dubbo", serialization = "hessian2")
public class LearningServiceImpl implements LearningService {

    private static final Logger logger = LoggerFactory.getLogger(LearningServiceImpl.class);
    private static final AtomicBoolean STACK_RECORDED = new AtomicBoolean();

    @Value("${learning.provider-id}")
    private String providerId;

    @Value("${learning.provider-delay-ms:0}")
    private long providerDelayMillis;

    @Override
    public LearningResponse greet(LearningRequest request) {
        String thread = Thread.currentThread().getName();
        String remoteAddress = RpcContext.getServiceContext().getRemoteAddressString();
        String localAddress = RpcContext.getServiceContext().getLocalAddressString();
        logger.info(
                "LEARNING_PROVIDER_RECEIVE requestId={} name={} providerId={} thread={} remoteAddress={} localAddress={}",
                request.getRequestId(),
                request.getName(),
                providerId,
                thread,
                remoteAddress,
                localAddress);

        if ("biz-failure".equals(request.getName())) {
            logger.info(
                    "LEARNING_PROVIDER_BIZ_FAILURE requestId={} providerId={} thread={}",
                    request.getRequestId(),
                    providerId,
                    thread);
            throw new IllegalStateException("学习实验预期业务异常：" + request.getRequestId());
        }

        if ("timeout".equals(request.getName()) && providerDelayMillis > 0) {
            logger.info(
                    "LEARNING_PROVIDER_DELAY requestId={} providerId={} delayMs={} thread={}",
                    request.getRequestId(),
                    providerId,
                    providerDelayMillis,
                    thread);
            try {
                Thread.sleep(providerDelayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("学习实验延迟被中断：" + request.getRequestId(), exception);
            }
        }

        if (STACK_RECORDED.compareAndSet(false, true)) {
            logger.info(
                    "LEARNING_PROVIDER_STACK requestId={}{}",
                    request.getRequestId(),
                    StackTraceFormatter.captureDubboFrames());
        }

        return new LearningResponse(request.getRequestId(), "你好，" + request.getName(), providerId);
    }
}
