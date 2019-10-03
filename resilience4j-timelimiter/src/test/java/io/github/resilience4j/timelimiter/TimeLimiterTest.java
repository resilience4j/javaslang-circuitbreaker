package io.github.resilience4j.timelimiter;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vavr.control.Try;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TimeLimiterTest {

    @Test
    public void shouldReturnCorrectTimeoutDuration() {
        Duration timeoutDuration = Duration.ofSeconds(1);
        TimeLimiter timeLimiter = TimeLimiter.of(timeoutDuration);
        then(timeLimiter).isNotNull();
        then(timeLimiter.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(timeoutDuration);
    }

    @Test
    public void shouldThrowTimeoutExceptionAndInvokeCancel()
        throws InterruptedException, ExecutionException, TimeoutException {
        Duration timeoutDuration = Duration.ofSeconds(1);
        TimeLimiter timeLimiter = TimeLimiter.of(timeoutDuration);

        @SuppressWarnings("unchecked")
        Future<Integer> mockFuture = (Future<Integer>) mock(Future.class);

        Supplier<Future<Integer>> supplier = () -> mockFuture;
        when(mockFuture.get(timeoutDuration.toMillis(), TimeUnit.MILLISECONDS))
            .thenThrow(new TimeoutException());

        Callable<Integer> decorated = TimeLimiter.decorateFutureSupplier(timeLimiter, supplier);
        Try<Integer> decoratedResult = Try.ofCallable(decorated);

        then(decoratedResult.isFailure()).isTrue();
        then(decoratedResult.getCause()).isInstanceOf(TimeoutException.class);

        verify(mockFuture).cancel(true);
    }

    @Test
    public void shouldThrowTimeoutExceptionAndNotInvokeCancel()
        throws InterruptedException, ExecutionException, TimeoutException {
        Duration timeoutDuration = Duration.ofSeconds(1);
        TimeLimiter timeLimiter = TimeLimiter
            .of(TimeLimiterConfig.custom().timeoutDuration(timeoutDuration)
                .cancelRunningFuture(false).build());

        @SuppressWarnings("unchecked")
        Future<Integer> mockFuture = (Future<Integer>) mock(Future.class);

        Supplier<Future<Integer>> supplier = () -> mockFuture;
        when(mockFuture.get(timeoutDuration.toMillis(), TimeUnit.MILLISECONDS))
            .thenThrow(new TimeoutException());

        Callable<Integer> decorated = TimeLimiter.decorateFutureSupplier(timeLimiter, supplier);
        Try<Integer> decoratedResult = Try.ofCallable(decorated);

        then(decoratedResult.isFailure()).isTrue();
        then(decoratedResult.getCause()).isInstanceOf(TimeoutException.class);

        verify(mockFuture, times(0)).cancel(true);
    }

    @Test
    public void shouldReturnResult() throws Exception {
        Duration timeoutDuration = Duration.ofSeconds(1);
        TimeLimiter timeLimiter = TimeLimiter.of(timeoutDuration);

        @SuppressWarnings("unchecked")
        Future<Integer> mockFuture = (Future<Integer>) mock(Future.class);

        Supplier<Future<Integer>> supplier = () -> mockFuture;
        when(mockFuture.get(timeoutDuration.toMillis(), TimeUnit.MILLISECONDS)).thenReturn(42);

        Integer result = timeLimiter.executeFutureSupplier(supplier);
        Assertions.assertThat(result).isEqualTo(42);

        result = timeLimiter.decorateFutureSupplier(supplier).call();
        Assertions.assertThat(result).isEqualTo(42);
    }

    @Test
    public void unwrapExecutionException() {
        TimeLimiter timeLimiter = TimeLimiter.ofDefaults();
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        Supplier<Future<Integer>> supplier = () -> executorService.submit(() -> {
            throw new RuntimeException();
        });
        Callable<Integer> decorated = TimeLimiter.decorateFutureSupplier(timeLimiter, supplier);

        Try<Integer> decoratedResult = Try.ofCallable(decorated);

        Assertions.assertThat(decoratedResult.getCause() instanceof RuntimeException).isTrue();
    }
}
