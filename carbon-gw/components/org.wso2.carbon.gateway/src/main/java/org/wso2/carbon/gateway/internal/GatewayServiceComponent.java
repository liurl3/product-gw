/*
 *  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.gateway.internal;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.kernel.transports.CarbonTransport;

/**
 * OSGi service component for Gateway.
 */
@Component(
        name = "org.wso2.carbon.gateway.internal.GatewayServiceComponent",
        immediate = true
)
@SuppressWarnings("unused")
public class GatewayServiceComponent {

    @Reference(
            name = "carbon-transport",
            service = CarbonTransport.class,
            cardinality = ReferenceCardinality.AT_LEAST_ONE,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "removeCarbonTransport"
    )
    protected void addCarbonTransport(CarbonTransport carbonTransport) {
        DataHolder.getInstance().addCarbonTransport(carbonTransport);
    }

    protected void removeCarbonTransport(CarbonTransport carbonTransport) {
        DataHolder.getInstance().removeCarbonTransport(carbonTransport);
    }
}
