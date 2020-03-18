package eu.arrowhead.kalix.util.concurrent;

import eu.arrowhead.kalix.util.Result;
import eu.arrowhead.kalix.util.function.ThrowingFunction;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@code Future} that always fails with a predetermined error.
 *
 * @param <V> Type of value that would have been included if successful.
 */
class FutureFailure<V> implements FutureProgress<V> {
    private final Throwable fault;

    /**
     * Creates new failing {@link Future}.
     *
     * @param fault Throwable to include in {@code Future}.
     * @throws NullPointerException If {@code fault} is {@code null}.
     */
    public FutureFailure(final Throwable fault) {
        this.fault = Objects.requireNonNull(fault);
    }

    @Override
    public void onResult(final Consumer<Result<V>> consumer) {
        Objects.requireNonNull(consumer, "Expected consumer");
        consumer.accept(Result.failure(fault));
    }

    @Override
    public void cancel(final boolean mayInterruptIfRunning) {
        // Does nothing.
    }

    @Override
    public void onFailure(final Consumer<Throwable> consumer) {
        Objects.requireNonNull(consumer);
        consumer.accept(fault);
    }

    @Override
    public FutureProgress<V> onProgress(final Listener listener) {
        Objects.requireNonNull(listener, "Expected listener");
        // Does nothing.
        return this;
    }

    @Override
    public <U> Future<U> map(final ThrowingFunction<? super V, U> mapper) {
        Objects.requireNonNull(mapper, "Expected mapper");
        return Future.failure(fault);
    }

    @Override
    public <U extends Throwable> Future<V> mapCatch(
        final Class<U> class_,
        final ThrowingFunction<U, ? extends V> mapper)
    {
        Objects.requireNonNull(class_, "Expected class_");
        Objects.requireNonNull(mapper, "Expected mapper");
        Throwable fault0;
        if (class_.isAssignableFrom(fault.getClass())) {
            try {
                return Future.success(mapper.apply(class_.cast(fault)));
            }
            catch (final Throwable throwable) {
                fault0 = throwable;
            }
        }
        else {
            fault0 = fault;
        }
        return Future.failure(fault0);
    }

    @Override
    public <U> Future<U> mapThrow(final ThrowingFunction<? super V, Throwable> mapper) {
        Objects.requireNonNull(mapper, "Expected mapper");
        return Future.failure(fault);
    }

    @Override
    public Future<V> mapFault(final ThrowingFunction<Throwable, Throwable> mapper) {
        Objects.requireNonNull(mapper, "Expected mapper");
        Throwable err;
        try {
            err = mapper.apply(fault);
        }
        catch (final Throwable throwable) {
            err = throwable;
        }
        return Future.failure(err);
    }

    @Override
    public <U> Future<U> mapResult(final ThrowingFunction<Result<V>, Result<U>> mapper) {
        Objects.requireNonNull(mapper, "Expected mapper");
        try {
            return new FutureResult<>(mapper.apply(Result.failure(fault)));
        }
        catch (final Throwable throwable) {
            return Future.failure(throwable);
        }
    }

    @Override
    public <U> Future<U> flatMap(final ThrowingFunction<? super V, ? extends Future<U>> mapper) {
        Objects.requireNonNull(mapper, "Expected mapper");
        return Future.failure(fault);
    }

    @Override
    public <U extends Throwable> Future<V> flatMapCatch(
        final Class<U> class_,
        final ThrowingFunction<U, ? extends Future<V>> mapper)
    {
        Objects.requireNonNull(class_, "Expected class_");
        Objects.requireNonNull(mapper, "Expected mapper");
        Throwable fault0;
        if (class_.isAssignableFrom(fault.getClass())) {
            try {
                return mapper.apply(class_.cast(fault));
            }
            catch (final Throwable throwable) {
                fault0 = throwable;
            }
        }
        else {
            fault0 = fault;
        }
        return Future.failure(fault0);
    }

    @Override
    public Future<V> flatMapFault(final ThrowingFunction<Throwable, ? extends Future<Throwable>> mapper) {
        Objects.requireNonNull(mapper, "Expected mapper");
        return new Future<>() {
            private Future<?> cancelTarget = null;
            private boolean isCancelled = false;

            @Override
            public void onResult(final Consumer<Result<V>> consumer) {
                if (isCancelled) {
                    return;
                }
                try {
                    final var future1 = mapper.apply(fault);
                    future1.onResult(result -> consumer.accept(Result.failure(result.isSuccess()
                        ? result.value()
                        : result.fault())));
                    cancelTarget = future1;
                }
                catch (final Throwable throwable) {
                    consumer.accept(Result.failure(throwable));
                }
            }

            @Override
            public void cancel(final boolean mayInterruptIfRunning) {
                isCancelled = true;
                if (cancelTarget != null) {
                    cancelTarget.cancel(mayInterruptIfRunning);
                    cancelTarget = null;
                }
            }
        };
    }

    @Override
    public <U> Future<U> flatMapResult(final ThrowingFunction<Result<V>, ? extends Future<U>> mapper) {
        try {
            return mapper.apply(Result.failure(fault));
        }
        catch (final Throwable throwable) {
            return Future.failure(throwable);
        }
    }

    @Override
    public <U> Future<U> pass(final U value) {
        Objects.requireNonNull(value, "Expected value");
        return Future.failure(fault);
    }

    @Override
    public <U> Future<U> fail(final Throwable throwable) {
        Objects.requireNonNull(throwable, "Expected throwable");
        throwable.addSuppressed(fault);
        return Future.failure(throwable);
    }
}
