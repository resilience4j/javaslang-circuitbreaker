/*
 * Copyright 2017 Robert Winkler
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
package io.github.resilience4j.circuitbreaker.annotation;

import io.github.resilience4j.recovery.DefaultRecoveryFunction;
import io.github.resilience4j.recovery.RecoveryFunction;

import java.lang.annotation.*;

/**
 * This annotation can be applied to a class or a specific method.
 * Applying it on a class is equivalent to applying it on all its public methods.
 * The annotation enables backend monitoring for all methods where it is applied.
 * Backend monitoring is performed via a circuit breaker.
 * See {@link io.github.resilience4j.circuitbreaker.CircuitBreaker} for details.
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface CircuitBreaker {

	/**
	 * Name of the circuit breaker.
	 *
	 * @return the name of the circuit breaker
	 */
	String name();

	/**
	 * @return the type of circuit breaker (default or webflux which is reactor circuit breaker)
	 */
	ApiType type() default ApiType.DEFAULT;

	Class<? extends RecoveryFunction> recovery() default DefaultRecoveryFunction.class;
}
