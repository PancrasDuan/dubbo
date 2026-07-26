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

import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.sourcelearning.api.LearningRequest;
import org.apache.dubbo.sourcelearning.api.LearningResponse;
import org.apache.dubbo.sourcelearning.api.LearningService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LearningClient {

    private static final Logger logger = LoggerFactory.getLogger(LearningClient.class);

    private final ReferenceBeanManager referenceBeanManager;

    @Value("${learning.call-count:3}")
    private int callCount;

    @Value("${learning.scenario:baseline}")
    private String scenario;

    @Value("${learning.retry-override:-1}")
    private int retryOverride;

    @Value("${learning.startup-delay-ms:0}")
    private long startupDelayMillis;

    @Value("${learning.frame-probe:false}")
    private boolean frameProbe;

    @DubboReference(
            url = "${learning.provider-url}",
            protocol = "dubbo",
            timeout = 3000,
            retries = 0,
            loadbalance = "roundrobin",
            check = true,
            filter = "learningConsumerStack")
    private LearningService learningService;

    public LearningClient(ReferenceBeanManager referenceBeanManager) {
        this.referenceBeanManager = referenceBeanManager;
    }

    public void runCalls() {
        if (frameProbe) {
            ProtocolFrameProbe.capture();
        }
        waitBeforeCalls();
        for (int sequence = 1; sequence <= callCount; sequence++) {
            String requestId = String.format("learning-%02d", sequence);
            String name = getRequestName(sequence);
            LearningRequest request = new LearningRequest(requestId, name);
            if (retryOverride >= 0) {
                RpcContext.getClientAttachment().setObjectAttachment("retries", retryOverride);
            }
            logger.info(
                    "LEARNING_CONSUMER_SEND requestId={} name={} scenario={} retryOverride={} thread={}",
                    requestId,
                    request.getName(),
                    scenario,
                    retryOverride,
                    Thread.currentThread().getName());

            try {
                LearningResponse response = learningService.greet(request);
                verify(request, response);
                logger.info(
                        "LEARNING_CONSUMER_RECEIVE requestId={} message={} providerId={} thread={}",
                        response.getRequestId(),
                        response.getMessage(),
                        response.getProviderId(),
                        Thread.currentThread().getName());
            } catch (RuntimeException exception) {
                if ("biz-failure".equals(scenario)) {
                    logger.info(
                            "LEARNING_EXPECTED_BIZ_FAILURE requestId={} exceptionType={} message={} thread={}",
                            requestId,
                            exception.getClass().getName(),
                            exception.getMessage(),
                            Thread.currentThread().getName());
                } else if ("timeout".equals(scenario)
                        && exception instanceof RpcException
                        && ((RpcException) exception).isTimeout()) {
                    logger.info(
                            "LEARNING_EXPECTED_TIMEOUT requestId={} exceptionType={} code={} message={} thread={}",
                            requestId,
                            exception.getClass().getName(),
                            ((RpcException) exception).getCode(),
                            exception.getMessage(),
                            Thread.currentThread().getName());
                } else {
                    throw exception;
                }
            }
        }
        logger.info("LEARNING_CALLS_COMPLETE scenario={} count={}", scenario, callCount);
        logInvokerTree();
    }

    private String getRequestName(int sequence) {
        if ("biz-failure".equals(scenario)) {
            return "biz-failure";
        }
        if ("timeout".equals(scenario)) {
            return "timeout";
        }
        return "Dubbo-" + sequence;
    }

    private void waitBeforeCalls() {
        if (startupDelayMillis <= 0) {
            return;
        }
        logger.info("LEARNING_CONSUMER_WAIT_BEFORE_CALLS delayMs={}", startupDelayMillis);
        try {
            Thread.sleep(startupDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待调用期间线程被中断", exception);
        }
    }

    private void logInvokerTree() {
        for (ReferenceBean<?> referenceBean : referenceBeanManager.getReferences()) {
            if (referenceBean.getReferenceConfig() != null
                    && referenceBean.getReferenceConfig().getInvoker() != null) {
                logger.info(
                        "LEARNING_CONSUMER_INVOKER_TREE interface={}{}",
                        referenceBean.getInterfaceClass().getName(),
                        InvokerTreeFormatter.format(
                                referenceBean.getReferenceConfig().getInvoker()));
            }
        }
    }

    private void verify(LearningRequest request, LearningResponse response) {
        if (!request.getRequestId().equals(response.getRequestId())) {
            throw new IllegalStateException("响应 requestId 与请求不一致");
        }
        if (response.getProviderId() == null || response.getProviderId().isEmpty()) {
            throw new IllegalStateException("响应未携带 providerId");
        }
    }
}
