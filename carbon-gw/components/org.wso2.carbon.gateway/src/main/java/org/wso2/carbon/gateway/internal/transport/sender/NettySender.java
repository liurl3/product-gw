/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.wso2.carbon.gateway.internal.transport.sender;

import com.lmax.disruptor.RingBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.internal.common.CarbonCallback;
import org.wso2.carbon.gateway.internal.common.CarbonMessage;
import org.wso2.carbon.gateway.internal.common.GWException;
import org.wso2.carbon.gateway.internal.common.TransportSender;
import org.wso2.carbon.gateway.internal.transport.common.Constants;
import org.wso2.carbon.gateway.internal.transport.common.HTTPContentChunk;
import org.wso2.carbon.gateway.internal.transport.common.HttpRoute;
import org.wso2.carbon.gateway.internal.transport.common.Util;
import org.wso2.carbon.gateway.internal.transport.common.disruptor.config.DisruptorConfig;
import org.wso2.carbon.gateway.internal.transport.common.disruptor.config.DisruptorFactory;
import org.wso2.carbon.gateway.internal.transport.listener.SourceHandler;
import org.wso2.carbon.gateway.internal.transport.sender.channel.TargetChannel;
import org.wso2.carbon.gateway.internal.transport.sender.channel.pool.ConnectionManager;
import org.wso2.carbon.transport.http.netty.listener.ssl.SSLConfig;

/**
 * A class creates connections with BE and send messages.
 */
public class NettySender implements TransportSender {

    private static final Logger log = LoggerFactory.getLogger(NettySender.class);
    private Config config;

    private ConnectionManager connectionManager;

    public NettySender(Config conf, ConnectionManager connectionManager) {
        this.config = conf;
        this.connectionManager = connectionManager;
    }

    @Override
    public boolean send(CarbonMessage msg, CarbonCallback callback) throws GWException {

        final HttpRequest httpRequest = Util.createHttpRequest(msg);

        final HttpRoute route = new HttpRoute(msg.getHost(), msg.getPort());

        SourceHandler srcHandler = (SourceHandler) msg.getProperty(Constants.SRC_HNDLR);

        RingBuffer ringBuffer = (RingBuffer) msg.getProperty(Constants.DISRUPTOR);
        if (ringBuffer == null) {
            DisruptorConfig disruptorConfig = DisruptorFactory.
                    getDisruptorConfig(DisruptorFactory.DisruptorType.OUTBOUND);
            ringBuffer = disruptorConfig.getDisruptor();
        }

        Channel outboundChannel = null;

        try {
            TargetChannel targetChannel = connectionManager.getTargetChannel(route, srcHandler);
            outboundChannel = targetChannel.getChannel();
            targetChannel.getTargetHandler().setCallback(callback);
            targetChannel.getTargetHandler().setRingBuffer(ringBuffer);
            targetChannel.getTargetHandler().setQueuesize(config.queueSize);
            targetChannel.getTargetHandler().setTargetChannel(targetChannel);
            targetChannel.getTargetHandler().setConnectionManager(connectionManager);

            writeContent(outboundChannel, httpRequest, msg);
        } catch (Exception failedCause) {
            throw new GWException(failedCause.getMessage(), failedCause);
        }

        return false;
    }

    private boolean writeContent(Channel channel, HttpRequest httpRequest, CarbonMessage carbonMessage) {
        channel.write(httpRequest);
        if (carbonMessage.getPipe().getMessageBytes() == null) {
            while (true) {
                HTTPContentChunk chunk = (HTTPContentChunk) carbonMessage.getPipe().getContent();
                HttpContent httpContent = chunk.getHttpContent();
                if (httpContent instanceof LastHttpContent) {
                    channel.writeAndFlush(httpContent);
                    break;
                }
                if (httpContent != null) {
                    channel.write(httpContent);
                }
            }
        } else {
            //If the pipe does not contain any HTTP chunks, write using the input stream
            ByteBuf contentBuf = carbonMessage.getPipe().getMessageBytes();
            if (contentBuf != null && contentBuf.isReadable()) {
                LastHttpContent httpContent = new DefaultLastHttpContent(contentBuf);
                final ChannelFuture f = channel.writeAndFlush(httpContent);
                f.addListener(new ChannelFutureListener2(f, contentBuf));
            }
        }
        return true;
    }

    /**
     * Class representing configs related to Transport Sender.
     */
    public static class Config {

        private String id;

        private SSLConfig sslConfig;

        private int queueSize;

        public Config(String id) {
            if (id == null) {
                throw new IllegalArgumentException("Netty transport ID is null");
            }
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public Config enableSsl(SSLConfig sslConfig) {
            this.sslConfig = sslConfig;
            return this;
        }

        public SSLConfig getSslConfig() {
            return sslConfig;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public Config setQueueSize(int queuesize) {
            this.queueSize = queuesize;
            return this;
        }

    }

    private static class ChannelFutureListener2 implements ChannelFutureListener {
        private ChannelFuture channelFuture;
        //private Channel channel;
        private ByteBuf contentBuf;

        public ChannelFutureListener2(ChannelFuture channelFuture, ByteBuf contentBuf) {
            this.channelFuture = channelFuture;
            //this.channel = channel;
            this.contentBuf = contentBuf;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            assert this.channelFuture == future;
            // Release the buffer and input stream
            if (contentBuf.refCnt() > 0) {
                contentBuf.release();
            }
        }
    }

}
