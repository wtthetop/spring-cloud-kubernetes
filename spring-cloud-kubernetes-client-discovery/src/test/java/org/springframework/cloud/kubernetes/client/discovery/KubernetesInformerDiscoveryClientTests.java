/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.HashMap;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesInformerDiscoveryClientTests {

	@Mock
	private SharedInformerFactory sharedInformerFactory;

	@Mock
	private KubernetesDiscoveryProperties kubernetesDiscoveryProperties;

	private static final V1Service testService1 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Service testService2 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace2"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Endpoints testEndpoints1 = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().port(8080))
					.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	private static final V1Service testServiceWithoutReadyAddresses = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-without-ready-addresses").namespace("namespace1"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Endpoints testEndpointsWithoutReadyAddresses = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-without-ready-addresses").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().port(8080))
					.addNotReadyAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	@Test
	public void testDiscoveryGetServicesAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
				sharedInformerFactory, serviceLister, null, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(testService1.getMetadata().getName(),
				testService2.getMetadata().getName());

		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
	}

	@Test
	public void testDiscoveryGetServicesOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, null, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(testService1.getMetadata().getName());

		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
	}

	@Test
	public void testDiscoveryGetInstanceAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1"))
				.containsOnly(new KubernetesServiceInstance("", "test-svc-1", "2.2.2.2", 8080, new HashMap<>(), false));

		verify(kubernetesDiscoveryProperties, times(2)).isAllNamespaces();
	}

	@Test
	public void testDiscoveryGetInstanceOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1"))
				.containsOnly(new KubernetesServiceInstance("", "test-svc-1", "2.2.2.2", 8080, new HashMap<>(), false));
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
	}

	@Test
	public void testDiscoveryGetInstanceWithoutReadyAddressesShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testServiceWithoutReadyAddresses);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointsWithoutReadyAddresses);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-without-ready-addresses")).isEmpty();
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).isIncludeNotReadyAddresses();
	}

	@Test
	public void testDiscoveryGetInstanceWithNotReadyAddressesIncludedShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testServiceWithoutReadyAddresses);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointsWithoutReadyAddresses);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);
		when(kubernetesDiscoveryProperties.isIncludeNotReadyAddresses()).thenReturn(true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-without-ready-addresses"))
				.containsOnly(new KubernetesServiceInstance("", "test-svc-without-ready-addresses", "2.2.2.2", 8080,
						new HashMap<>(), false));
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).isIncludeNotReadyAddresses();
	}

	private Lister<V1Service> setupServiceLister(V1Service... services) {
		Cache<V1Service> serviceCache = new Cache<>();
		Lister<V1Service> serviceLister = new Lister<>(serviceCache);
		for (V1Service svc : services) {
			serviceCache.add(svc);
		}
		return serviceLister;
	}

	private Lister<V1Endpoints> setupEndpointsLister(V1Endpoints... endpoints) {
		Cache<V1Endpoints> endpointsCache = new Cache<>();
		Lister<V1Endpoints> endpointsLister = new Lister<>(endpointsCache);
		for (V1Endpoints ep : endpoints) {
			endpointsCache.add(ep);
		}
		return endpointsLister;
	}

}
