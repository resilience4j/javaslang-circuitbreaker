/*
 * Copyright 2019 Ingyu Hwang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.resilience4j.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.codahale.metrics.MetricRegistry;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.test.HelloWorldService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.BDDMockito;

public abstract class AbstractBulkheadMetricsTest {

    private static final int DEFAULT_MAX_CONCURRENT_CALLS = BulkheadConfig.ofDefaults()
        .getMaxConcurrentCalls();
    private MetricRegistry metricRegistry;
    private HelloWorldService helloWorldService;
    private ExecutorService executorService;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
        helloWorldService = mock(HelloWorldService.class);
        executorService = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() throws Exception {
        executorService.shutdown();
    }

    protected abstract Bulkhead given(String prefix, MetricRegistry metricRegistry);

    protected abstract Bulkhead given(MetricRegistry metricRegistry);

    @Test
    public void shouldRegisterMetrics() throws Throwable {
        // Given
        Bulkhead bulkhead = given(metricRegistry);

        // Given latch to verify bulkhead
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // Given the HelloWorldService returns Hello world
        BDDMockito.given(helloWorldService.returnHelloWorld()).will(invocation -> {
            if (countDownLatch.await(10, TimeUnit.SECONDS)) {
                return "Hello world";
            } else {
                throw new IllegalStateException("Timeout - test failure");
            }
        });

        //When
        Future<String> future = executorService
            .submit(() -> bulkhead.executeSupplier(helloWorldService::returnHelloWorld));

        // Then metrics are present and show value
        assertThat(metricRegistry.getMetrics()).hasSize(2);
        assertThat(metricRegistry.getGauges()
            .get("resilience4j.bulkhead.testBulkhead.available_concurrent_calls").getValue())
            .isIn(DEFAULT_MAX_CONCURRENT_CALLS, DEFAULT_MAX_CONCURRENT_CALLS - 1);
        assertThat(metricRegistry.getGauges()
            .get("resilience4j.bulkhead.testBulkhead.max_allowed_concurrent_calls").getValue())
            .isEqualTo(DEFAULT_MAX_CONCURRENT_CALLS);

        // Then release latch and verify result
        countDownLatch.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("Hello world");
        BDDMockito.then(helloWorldService).should(times(1)).returnHelloWorld();

        // Then check metrics again
        assertThat(metricRegistry.getMetrics()).hasSize(2);
        assertThat(metricRegistry.getGauges()
            .get("resilience4j.bulkhead.testBulkhead.available_concurrent_calls").getValue())
            .isIn(DEFAULT_MAX_CONCURRENT_CALLS, DEFAULT_MAX_CONCURRENT_CALLS);
        assertThat(metricRegistry.getGauges()
            .get("resilience4j.bulkhead.testBulkhead.max_allowed_concurrent_calls").getValue())
            .isEqualTo(DEFAULT_MAX_CONCURRENT_CALLS);
    }

    @Test
    public void shouldUseCustomPrefix() throws Throwable {
        //Given
        Bulkhead bulkhead = given("testPre", metricRegistry);

        // Given latch to verify bulkhead
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // Given the HelloWorldService returns Hello world
        BDDMockito.given(helloWorldService.returnHelloWorld()).will(invocation -> {
            if (countDownLatch.await(10, TimeUnit.SECONDS)) {
                return "Hello world";
            } else {
                throw new IllegalStateException("Timeout - test failure");
            }
        });

        //When
        Future<String> future = executorService
            .submit(() -> bulkhead.executeSupplier(helloWorldService::returnHelloWorld));

        // Then metrics are present and show value
        assertThat(metricRegistry.getMetrics()).hasSize(2);
        assertThat(metricRegistry.getGauges().get("testPre.testBulkhead.available_concurrent_calls")
            .getValue())
            .isIn(DEFAULT_MAX_CONCURRENT_CALLS, DEFAULT_MAX_CONCURRENT_CALLS - 1);
        assertThat(
            metricRegistry.getGauges().get("testPre.testBulkhead.max_allowed_concurrent_calls")
                .getValue())
            .isEqualTo(DEFAULT_MAX_CONCURRENT_CALLS);

        // Then release latch and verify result
        countDownLatch.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("Hello world");
        BDDMockito.then(helloWorldService).should(times(1)).returnHelloWorld();

        // Then check metrics again
        assertThat(metricRegistry.getMetrics()).hasSize(2);
        assertThat(metricRegistry.getGauges().get("testPre.testBulkhead.available_concurrent_calls")
            .getValue())
            .isIn(DEFAULT_MAX_CONCURRENT_CALLS, DEFAULT_MAX_CONCURRENT_CALLS);
        assertThat(
            metricRegistry.getGauges().get("testPre.testBulkhead.max_allowed_concurrent_calls")
                .getValue())
            .isEqualTo(DEFAULT_MAX_CONCURRENT_CALLS);
    }

    @Test
    public void bulkheadConfigChangeAffectsTheMaxAllowedConcurrentCallsValue() {
        // Given
        Bulkhead bulkhead = given("testPre", metricRegistry);

        // Then make sure that configured value is reported as max allowed concurrent calls
        assertThat(
            metricRegistry.getGauges().get("testPre.testBulkhead.max_allowed_concurrent_calls")
                .getValue())
            .isEqualTo(DEFAULT_MAX_CONCURRENT_CALLS);

        // And when the config is changed
        BulkheadConfig newConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(DEFAULT_MAX_CONCURRENT_CALLS + 50)
            .build();
        bulkhead.changeConfig(newConfig);

        // Then the new config value gets reported
        assertThat(
            metricRegistry.getGauges().get("testPre.testBulkhead.max_allowed_concurrent_calls")
                .getValue())
            .isEqualTo(newConfig.getMaxConcurrentCalls());
    }
}
