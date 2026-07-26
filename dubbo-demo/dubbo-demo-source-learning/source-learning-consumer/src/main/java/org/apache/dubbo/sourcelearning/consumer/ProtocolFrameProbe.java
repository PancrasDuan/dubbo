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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;
import org.apache.dubbo.remoting.buffer.ChannelBuffers;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.protocol.dubbo.DubboCodec;
import org.apache.dubbo.sourcelearning.api.LearningRequest;
import org.apache.dubbo.sourcelearning.api.LearningService;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;

final class ProtocolFrameProbe {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolFrameProbe.class);

    private static final int HEADER_LENGTH = 16;
    private static final long REQUEST_ID = 0x0102030405060708L;
    private static final URL CHANNEL_URL =
            URL.valueOf("dubbo://127.0.0.1:20880/" + LearningService.class.getName() + "?serialization=hessian2");

    private ProtocolFrameProbe() {}

    static void capture() {
        try {
            RpcInvocation invocation = createInvocation();
            Request request = new Request(REQUEST_ID);
            request.setVersion(Version.getProtocolVersion());
            request.setTwoWay(true);
            request.setData(invocation);

            ChannelBuffer buffer = ChannelBuffers.dynamicBuffer(1024);
            new DubboCodec(FrameworkModel.defaultModel()).encode(new ProbeChannel(), buffer, request);

            byte[] frame = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), frame);
            Header header = Header.parse(frame);
            DecodedBody body = decodeBody(frame, header);

            logger.info(
                    "LEARNING_DUBBO_FRAME headerHex={} frameBytes={} bodyBytes={} magic={} flags={} request={} twoWay={} event={} serializationId={} statusOrReserved={} requestId={} bodyPrefixHex={}",
                    toHex(frame, 0, HEADER_LENGTH),
                    frame.length,
                    header.bodyLength,
                    hexByte(header.magicHigh) + hexByte(header.magicLow),
                    hexByte(header.flags),
                    header.request,
                    header.twoWay,
                    header.event,
                    header.serializationId,
                    header.statusOrReserved,
                    header.requestId,
                    toHex(frame, HEADER_LENGTH, Math.min(header.bodyLength, 96)));
            logger.info(
                    "LEARNING_DUBBO_BODY_DECODED dubboVersion={} path={} serviceVersion={} method={} parameterTypesDesc={} requestId={} name={} attachmentKeys={}",
                    body.dubboVersion,
                    body.path,
                    body.serviceVersion,
                    body.method,
                    body.parameterTypesDesc,
                    body.argument.getRequestId(),
                    body.argument.getName(),
                    body.attachments.keySet());
        } catch (Exception exception) {
            throw new IllegalStateException("生成 Dubbo 请求帧失败", exception);
        }
    }

    private static RpcInvocation createInvocation() {
        RpcInvocation invocation = new RpcInvocation(
                null,
                "greet",
                LearningService.class.getName(),
                "",
                new Class<?>[] {LearningRequest.class},
                new Object[] {new LearningRequest("frame-01", "Protocol-Probe")});
        invocation.setObjectAttachment(INTERFACE_KEY, LearningService.class.getName());
        invocation.setObjectAttachment(PATH_KEY, LearningService.class.getName());
        invocation.setObjectAttachment(VERSION_KEY, "0.0.0");
        invocation.setObjectAttachment(TIMEOUT_KEY, "3000");
        invocation.setTargetServiceUniqueName(LearningService.class.getName());
        return invocation;
    }

    private static DecodedBody decodeBody(byte[] frame, Header header) throws Exception {
        Serialization serialization = CodecSupport.getSerializationById((byte) header.serializationId);
        ObjectInput input = serialization.deserialize(
                CHANNEL_URL, new ByteArrayInputStream(frame, HEADER_LENGTH, header.bodyLength));
        DecodedBody body = new DecodedBody();
        body.dubboVersion = input.readUTF();
        body.path = input.readUTF();
        body.serviceVersion = input.readUTF();
        body.method = input.readUTF();
        body.parameterTypesDesc = input.readUTF();
        body.argument = input.readObject(LearningRequest.class);
        body.attachments = input.readAttachments();
        return body;
    }

    private static String toHex(byte[] bytes, int offset, int length) {
        StringBuilder result = new StringBuilder(length * 2);
        for (int index = offset; index < offset + length; index++) {
            result.append(hexByte(bytes[index]));
        }
        return result.toString();
    }

    private static String hexByte(byte value) {
        return String.format("%02x", value & 0xff);
    }

    private static final class Header {

        private byte magicHigh;
        private byte magicLow;
        private byte flags;
        private int statusOrReserved;
        private boolean request;
        private boolean twoWay;
        private boolean event;
        private int serializationId;
        private long requestId;
        private int bodyLength;

        private static Header parse(byte[] frame) {
            if (frame.length < HEADER_LENGTH) {
                throw new IllegalArgumentException("Dubbo 帧不足 16 字节");
            }
            Header header = new Header();
            header.magicHigh = frame[0];
            header.magicLow = frame[1];
            header.flags = frame[2];
            header.statusOrReserved = frame[3] & 0xff;
            header.request = (header.flags & 0x80) != 0;
            header.twoWay = (header.flags & 0x40) != 0;
            header.event = (header.flags & 0x20) != 0;
            header.serializationId = header.flags & 0x1f;
            header.requestId = readLong(frame, 4);
            header.bodyLength = readInt(frame, 12);
            if (header.bodyLength != frame.length - HEADER_LENGTH) {
                throw new IllegalArgumentException("Dubbo body 长度与帧长度不一致");
            }
            return header;
        }

        private static long readLong(byte[] bytes, int offset) {
            long result = 0;
            for (int index = offset; index < offset + 8; index++) {
                result = (result << 8) | (bytes[index] & 0xffL);
            }
            return result;
        }

        private static int readInt(byte[] bytes, int offset) {
            int result = 0;
            for (int index = offset; index < offset + 4; index++) {
                result = (result << 8) | (bytes[index] & 0xff);
            }
            return result;
        }
    }

    private static final class DecodedBody {

        private String dubboVersion;
        private String path;
        private String serviceVersion;
        private String method;
        private String parameterTypesDesc;
        private LearningRequest argument;
        private Map<String, Object> attachments = Collections.emptyMap();
    }

    private static final class ProbeChannel implements Channel {

        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 20880);
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean hasAttribute(String key) {
            return attributes.containsKey(key);
        }

        @Override
        public Object getAttribute(String key) {
            return attributes.get(key);
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public void removeAttribute(String key) {
            attributes.remove(key);
        }

        @Override
        public URL getUrl() {
            return CHANNEL_URL;
        }

        @Override
        public ChannelHandler getChannelHandler() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 32000);
        }

        @Override
        public void send(Object message) throws RemotingException {}

        @Override
        public void send(Object message, boolean sent) throws RemotingException {}

        @Override
        public void close() {}

        @Override
        public void close(int timeout) {}

        @Override
        public void startClose() {}

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
