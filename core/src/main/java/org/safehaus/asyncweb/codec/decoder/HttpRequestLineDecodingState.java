/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.safehaus.asyncweb.codec.decoder;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.apache.mina.common.IoBuffer;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.statemachine.CRLFDecodingState;
import org.apache.mina.filter.codec.statemachine.ConsumeToDynamicTerminatorDecodingState;
import org.apache.mina.filter.codec.statemachine.ConsumeToLinearWhitespaceDecodingState;
import org.apache.mina.filter.codec.statemachine.DecodingState;
import org.apache.mina.filter.codec.statemachine.DecodingStateMachine;
import org.apache.mina.filter.codec.statemachine.LinearWhitespaceSkippingState;
import org.safehaus.asyncweb.codec.HttpCodecUtils;
import org.safehaus.asyncweb.common.HttpMethod;
import org.safehaus.asyncweb.common.HttpResponseStatus;
import org.safehaus.asyncweb.common.HttpVersion;

/**
 * Decodes an HTTP request line - populating an associated <code>Request</code>
 * This decoder expects the following format:
 * <pre>
 * Request-Line = Method SP Request-URI SP HTTP-Version CRLF
 * </pre>
 *
 * @author irvingd
 *
 */
abstract class HttpRequestLineDecodingState extends DecodingStateMachine {

    private static final Charset US_ASCII = Charset.forName("US-ASCII");

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final CharsetDecoder asciiDecoder = US_ASCII.newDecoder();

    private final CharsetDecoder utf8Decoder = UTF_8.newDecoder();

    @Override
    protected DecodingState init() throws Exception {
        return READ_METHOD;
    }

    @Override
    protected void destroy() throws Exception {
    }

    private final DecodingState READ_METHOD = new ConsumeToLinearWhitespaceDecodingState() {
        @Override
        protected DecodingState finishDecode(IoBuffer product,
                ProtocolDecoderOutput out) throws Exception {
            HttpMethod method = HttpMethod.valueOf(product
                    .getString(asciiDecoder));

            if (method == null) {
                HttpCodecUtils.throwDecoderException("Bad method",
                        HttpResponseStatus.NOT_IMPLEMENTED);
            }

            out.write(method);

            return AFTER_READ_METHOD;
        }
    };

    private final DecodingState AFTER_READ_METHOD = new LinearWhitespaceSkippingState() {
        @Override
        protected DecodingState finishDecode(int skippedBytes) throws Exception {
            return READ_REQUEST_URI;
        }
    };

    private final DecodingState READ_REQUEST_URI = new ConsumeToLinearWhitespaceDecodingState() {
        @Override
        protected DecodingState finishDecode(IoBuffer product,
                ProtocolDecoderOutput out) throws Exception {
            out.write(new URI(product.getString(utf8Decoder)));
            return AFTER_READ_REQUEST_URI;
        }
    };

    private final DecodingState AFTER_READ_REQUEST_URI = new LinearWhitespaceSkippingState() {
        @Override
        protected DecodingState finishDecode(int skippedBytes) throws Exception {
            return READ_PROTOCOL_VERSION;
        }
    };

    private final DecodingState READ_PROTOCOL_VERSION = new ConsumeToDynamicTerminatorDecodingState() {
        @Override
        protected DecodingState finishDecode(IoBuffer product,
                ProtocolDecoderOutput out) throws Exception {
            String versionStr = product.getString(asciiDecoder);
            HttpVersion version = HttpVersion.valueOf(versionStr);
            if (version == null) {
                HttpCodecUtils.throwDecoderException(
                        "Unsupported HTTP version: " + versionStr,
                        HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED);
            }
            out.write(version);
            return AFTER_READ_PROTOCOL_VERSION;
        }

        @Override
        protected boolean isTerminator(byte b) {
            return Character.isWhitespace(b);
        }
    };

    private final DecodingState AFTER_READ_PROTOCOL_VERSION = new LinearWhitespaceSkippingState() {
        @Override
        protected DecodingState finishDecode(int skippedBytes) throws Exception {
            return FINISH;
        }
    };

    private final DecodingState FINISH = new CRLFDecodingState() {
        @Override
        protected DecodingState finishDecode(boolean foundCRLF,
                ProtocolDecoderOutput out) throws Exception {
            if (!foundCRLF) {
                HttpCodecUtils
                        .throwDecoderException("Expected CR/LF at the end of the request line.");
            }

            return null;
        }
    };
}
