/*
 * Copyright 2017 Dan Maas
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

package io.github.resilience4j.ratpack.annotation

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.ratpack.RecoveryFunction
import io.github.resilience4j.ratpack.ResilienceModule
import io.reactivex.Flowable
import io.reactivex.functions.Function
import ratpack.exec.Promise
import ratpack.test.embed.EmbeddedApp
import ratpack.test.http.TestHttpClient
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

@Unroll
class CircuitBreakerSpec extends Specification {

    @AutoCleanup
    EmbeddedApp app

    @Delegate
    TestHttpClient client

    def "test circuit break a method via annotation with fallback"() {
        given:
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(buildConfig())
        app = ratpack {
            bindings {
                bindInstance(CircuitBreakerRegistry, registry)
                bindInstance(RateLimiterRegistry, RateLimiterRegistry.of(RateLimiterConfig.ofDefaults()))
                bind(Something)
                module(ResilienceModule)
            }
            handlers {
                get('promise') { Something something ->
                    something.breakerPromise().then {
                        render it
                    }
                }
                get('promiseBad') { Something something ->
                    something.breakerPromiseBad().then {
                        render it
                    }
                }
                get('promiseRecover') { Something something ->
                    something.breakerPromiseRecovery().then {
                        render it
                    }
                }
                get('stage') { Something something ->
                    render something.breakerStage().toCompletableFuture().get()
                }
                get('stageBad') { Something something ->
                    render something.breakerStageBad().toCompletableFuture().get()
                }
                get('stageRecover') { Something something ->
                    render something.breakerStageRecover().toCompletableFuture().get()
                }
                get('flow') { Something something ->
                    something.breakerFlow().subscribe {
                        render it
                    }
                }
                get('flowBad') { Something something ->
                    something.breakerFlowBad().subscribe {
                        render it
                    }
                }
                get('flowRecover') { Something something ->
                    something.breakerFlowRecover().subscribe {
                        render it
                    }
                }
                get('normal') { Something something ->
                    render something.breakerNormal()
                }
                get('normalBad') { Something something ->
                    render something.breakerNormalBad()
                }
                get('normalRecover') { Something something ->
                    render something.breakerNormalRecover()
                }
            }
        }
        client = testHttpClient(app)
        def breaker = registry.circuitBreaker(breakerName)

        when:
        def actual = get(path)

        then:
        actual.body.text == expectedText
        actual.statusCode == 200
        breaker.callPermitted
        breaker.state == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED

        when:
        get(badPath)
        actual = get(badPath)

        then:
        actual.statusCode == badStatus
        !breaker.callPermitted
        breaker.state == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN

        when:
        get(recoverPath)
        actual = get(recoverPath)

        then:
        actual.body.text == "recovered"
        actual.statusCode == 200
        !breaker.callPermitted
        breaker.state == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN

        where:
        path      | badPath      | recoverPath      | breakerName | expectedText      | badStatus
        'promise' | 'promiseBad' | 'promiseRecover' | 'test'      | 'breaker promise' | 500
        'stage'   | 'stageBad'   | 'stageRecover'   | 'test'      | 'breaker stage'   | 404
        'flow'    | 'flowBad'    | 'flowRecover'    | 'test'      | 'breaker flow'    | 500
        'normal'  | 'normalBad'  | 'normalRecover'  | 'test'      | 'breaker normal'  | 404
    }

    def buildConfig() {
        CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .ringBufferSizeInHalfOpenState(2)
                .ringBufferSizeInClosedState(2)
                .build()
    }

    static class Something {

        @CircuitBreaker(name = "test")
        Promise<String> breakerPromise() {
            Promise.async {
                it.success("breaker promise")
            }
        }

        @CircuitBreaker(name = "test")
        Promise<String> breakerPromiseBad() {
            Promise.async {
                it.error(new Exception("breaker promise bad"))
            }
        }

        @CircuitBreaker(name = "test", recovery = MyRecoveryFunction)
        Promise<String> breakerPromiseRecovery() {
            Promise.async {
                it.error(new Exception("breaker promise bad"))
            }
        }

        @CircuitBreaker(name = "test")
        CompletionStage<String> breakerStage() {
            CompletableFuture.supplyAsync { 'breaker stage' }
        }

        @CircuitBreaker(name = "test")
        CompletionStage<Void> breakerStageBad() {
            CompletableFuture.supplyAsync { throw new RuntimeException("bad") }
        }

        @CircuitBreaker(name = "test", recovery = MyRecoveryFunction)
        CompletionStage<Void> breakerStageRecover() {
            CompletableFuture.supplyAsync { throw new RuntimeException("bad") }
        }

        @CircuitBreaker(name = "test")
        Flowable<String> breakerFlow() {
            Flowable.just("breaker flow")
        }

        @CircuitBreaker(name = "test")
        Flowable<Void> breakerFlowBad() {
            Flowable.just("breaker flow").map({ throw new Exception("bad") } as Function<String, Void>)
        }

        @CircuitBreaker(name = "test", recovery = MyRecoveryFunction)
        Flowable<Void> breakerFlowRecover() {
            Flowable.just("breaker flow").map({ throw new Exception("bad") } as Function<String, Void>)
        }

        @CircuitBreaker(name = "test")
        String breakerNormal() {
            "breaker normal"
        }

        @CircuitBreaker(name = "test")
        String breakerNormalBad() {
            throw new Exception("bad")
        }

        @CircuitBreaker(name = "test", recovery = MyRecoveryFunction)
        String breakerNormalRecover() {
            throw new Exception("bad")
        }
    }

    static class MyRecoveryFunction implements RecoveryFunction<String> {
        @Override
        String apply(Throwable t) throws Exception {
            "recovered"
        }
    }

}