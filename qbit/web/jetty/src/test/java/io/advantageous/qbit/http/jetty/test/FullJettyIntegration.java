/*
 * Copyright (c) 2015. Rick Hightower, Geoff Chandler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * QBit - The Microservice lib for Java : JSON, WebSocket, REST. Be The Web!
 */

package io.advantageous.qbit.http.jetty.test;

import io.advantageous.boon.core.Str;
import io.advantageous.boon.core.Sys;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.client.Client;
import io.advantageous.qbit.client.ClientBuilder;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.http.client.HttpClientBuilder;
import io.advantageous.qbit.http.request.HttpRequest;
import io.advantageous.qbit.http.request.HttpRequestBuilder;
import io.advantageous.qbit.http.request.HttpTextReceiver;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.server.ServiceServer;
import io.advantageous.qbit.server.ServiceServerBuilder;
import io.advantageous.qbit.service.ServiceProxyUtils;
import io.advantageous.qbit.test.TimedTesting;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.advantageous.boon.core.Exceptions.die;
import static io.advantageous.boon.core.IO.puts;


/**
 * @author rhightower on 2/14/15.
 */
public class FullJettyIntegration extends TimedTesting {


    static volatile int port = 7777;
    private AtomicReference<String> returnValue;

    Client client;
    ServiceServer server;
    HttpClient httpClient;
    ClientServiceInterface clientProxy;
    AtomicInteger callCount;
    AtomicReference<String> pongValue;
    boolean ok;
    AtomicInteger returnCount;

    @Test
    public void testWebSocket() throws Exception {


        clientProxy.ping(s -> {
            puts(s);
            pongValue.set(s);
        }, "hi");

        ServiceProxyUtils.flushServiceProxy(clientProxy);

        waitForTrigger(20, o -> this.pongValue.get() != null);


        final String pongValue = this.pongValue.get();
        ok = pongValue.equals("hi pong") || die();

    }

    @Test
    public void testWebSocketFlushHappy() throws Exception {


        final Callback<String> callback = s -> {
            int count = returnCount.incrementAndGet();

            if (count % 2 == 0) {
                puts("return count", count);
            }

            puts("                     PONG");
            pongValue.set(s);
        };

        for (int index = 0; index < 11; index++) {

            clientProxy.ping(callback, "hi");

        }

        ServiceProxyUtils.flushServiceProxy(clientProxy);
        Sys.sleep(100);

        client.flush();
        Sys.sleep(100);


        waitForTrigger(20, o -> returnCount.get() >= callCount.get());

        puts("HERE                        ", callCount, returnCount);

        ok = returnCount.get() >= callCount.get() || die(returnCount, callCount); //TODO off by one error?


    }

    @Test
    public void testWebSocketSend10() throws Exception {


        final Callback<String> callback = s -> {

            returnCount.incrementAndGet();

            puts("                     PONG");
            pongValue.set(s);
        };

        for (int index = 0; index < 10; index++) {

            clientProxy.ping(callback, "hi");

        }

        ServiceProxyUtils.flushServiceProxy(clientProxy);
        Sys.sleep(100);


        client.flush();
        Sys.sleep(100);


        waitForTrigger(20, o -> returnCount.get() == callCount.get());

        puts("HERE                        ", callCount, returnCount);

        ok = returnCount.get() == callCount.get() || die(returnCount, callCount);


    }

    @Test
    public void testRestCallSimple() throws Exception {

        final HttpRequest request = new HttpRequestBuilder()
                .setUri("/services/mockservice/ping")
                .setJsonBodyForPost("\"hello\"")
                .setTextReceiver(new HttpTextReceiver() {
                    @Override
                    public void response(int code, String mimeType, String body) {
                        if (code == 200) {
                            pongValue.set(body);
                        } else {
                            pongValue.set("ERROR " + body + " code " + code);
                            throw new RuntimeException("ERROR " + code + " " + body);

                        }
                    }
                })
                .build();

        httpClient.sendHttpRequest(request);

        httpClient.flush();

        waitForTrigger(20, o -> this.pongValue.get() != null);


        final String pongValue = this.pongValue.get();
        ok = pongValue.equals("\"hello pong\"") || die("message##", pongValue, "##message");

    }

    @Test
    public void testRestWithTwoParams() throws Exception {

        final HttpRequest request = new HttpRequestBuilder()
                .setUri("/services/mockservice/pingWithTwoParams")
                .setJsonBodyForPost("\"hello\"")
                .addParam("param1", "param1")
                .addParam("param2", "param2")
                .setTextReceiver(new HttpTextReceiver() {
                    @Override
                    public void response(int code, String mimeType, String body) {
                        if (code == 200) {
                            pongValue.set(body);
                        } else {
                            pongValue.set("ERROR " + body + " code " + code);
                            throw new RuntimeException("ERROR " + code + " " + body);

                        }
                    }
                })
                .build();

        httpClient.sendHttpRequest(request);

        httpClient.flush();

        waitForTrigger(20, o -> this.pongValue.get() != null);


        final String pongValue = this.pongValue.get();
        ok = pongValue.equals("\"hello pong\"") || die("message##", pongValue, "##message");

        puts("RETURN VALUE", returnValue.get());
    }

    @Before
    public synchronized void setup() throws Exception {


        returnValue = new AtomicReference<>();

        returnCount = new AtomicInteger();

        super.setupLatch();

        returnValue.set("");
        port += 10;
        pongValue = new AtomicReference<>();

        callCount = new AtomicInteger();

        httpClient = new HttpClientBuilder().setPort(port).build();

        puts("PORT", port);

        client = new ClientBuilder().setPort(port).build();
        server = new ServiceServerBuilder().setPort(port).build();

        server.initServices(new MockService());

        server.start();

        Sys.sleep(200);

        clientProxy = client.createProxy(ClientServiceInterface.class, "mockService");
        Sys.sleep(100);
        httpClient.start();
        Sys.sleep(100);
        client.start();

        pongValue.set(null);

        Sys.sleep(200);


    }

    @After
    public void teardown() throws Exception {

        port++;


        Sys.sleep(200);
        server.stop();
        Sys.sleep(200);
        client.stop();
        httpClient.stop();
        Sys.sleep(200);
        server = null;
        client = null;
        System.gc();
        Sys.sleep(1000);

        returnValue.set("");

    }


    static interface ClientServiceInterface {
        String ping(Callback<String> callback, String ping);
    }

    class MockService {

        @RequestMapping(method = RequestMethod.POST)
        public String ping(String ping) {
            callCount.incrementAndGet();
            return ping + " pong";
        }


        @RequestMapping(method = RequestMethod.POST)
        public String pingWithTwoParams(final String ping,
                                        final @RequestParam("param1") String param1,
                                        final @RequestParam("param2") String param2) {

            callCount.incrementAndGet();

            returnValue.set(Str.join('-', ping, param1, param2));
            return ping + " pong";
        }
    }
}

