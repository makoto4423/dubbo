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
package org.apache.dubbo.demo.provider;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.*;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.demo.GreetingService;

import java.util.Collections;

public class Application {

    private static final String REGISTRY_URL = "zookeeper://127.0.0.1:2181";

    public static void main(String[] args) {
        startWithBootstrap();
    }

    private static void startWithBootstrap() {
        // 同样是 dubbo， 使用api，provider重复注册，会忽略后一个重复provider的注册
        ServiceConfig<DemoServiceImpl> service = new ServiceConfig<>();
        service.setInterface(DemoService.class);
        service.setParameters(Collections.singletonMap("DemoServiceImpl","DemoServiceImpl"));
        service.setRef(new DemoServiceImpl());

        ServiceConfig<DemoServiceImpl2> service2 = new ServiceConfig<>();
        service2.setInterface(DemoService.class);
        service2.setParameters(Collections.singletonMap("ServiceConfig","ServiceConfig"));
        service2.setRef(new DemoServiceImpl2());

        ServiceConfig<GreetingService> greet = new ServiceConfig<>();
        greet.setInterface(GreetingService.class);
        greet.setRef(new GreetingServiceImpl());

//        ProtocolConfig protocolConfig = new ProtocolConfig(CommonConstants.DUBBO, -1);
//        protocolConfig.setHost("192.168.2.117");

        RegistryConfig registryConfig = new RegistryConfig(REGISTRY_URL);
        registryConfig.setRegisterMode("interface");
        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        bootstrap.application(new ApplicationConfig("dubbo-demo-api-provider"))
            .registry(registryConfig)
            .metadataReport(new MetadataReportConfig("zookeeper://127.0.0.1:2182"))
//            .protocol(protocolConfig)
            .service(service)
            .service(greet)
            .start()
            .await();
    }

}
