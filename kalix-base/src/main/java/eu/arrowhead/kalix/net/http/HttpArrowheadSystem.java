package eu.arrowhead.kalix.net.http;

import eu.arrowhead.kalix.ArrowheadSystem;
import eu.arrowhead.kalix.descriptor.InterfaceDescriptor;
import eu.arrowhead.kalix.descriptor.ServiceDescriptor;
import eu.arrowhead.kalix.descriptor.TransportDescriptor;
import eu.arrowhead.kalix.internal.net.NettyBootstraps;
import eu.arrowhead.kalix.internal.net.http.NettyHttpServiceConnectionInitializer;
import eu.arrowhead.kalix.internal.util.concurrent.NettyScheduler;
import eu.arrowhead.kalix.internal.util.logging.LogLevels;
import eu.arrowhead.kalix.net.http.service.HttpService;
import eu.arrowhead.kalix.net.http.service.HttpServiceRequest;
import eu.arrowhead.kalix.net.http.service.HttpServiceResponse;
import eu.arrowhead.kalix.util.Result;
import eu.arrowhead.kalix.util.concurrent.Future;
import io.netty.channel.ChannelFuture;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An {@link ArrowheadSystem} that provides {@link HttpService}s.
 */
public class HttpArrowheadSystem extends ArrowheadSystem<HttpService> {
    private final AtomicReference<InetSocketAddress> localSocketAddress = new AtomicReference<>();
    private final TreeMap<String, HttpService> providedServices = new TreeMap<>();

    // Created when requested.
    private ServiceDescriptor[] providedServiceDescriptors = null;

    private HttpArrowheadSystem(final Builder builder) {
        super(builder);
    }

    @Override
    public InetAddress localAddress() {
        final var socketAddress = localSocketAddress.get();
        return socketAddress != null
            ? socketAddress.getAddress()
            : super.localAddress();
    }

    @Override
    public int localPort() {
        final var socketAddress = localSocketAddress.get();
        return socketAddress != null
            ? socketAddress.getPort()
            : super.localPort();
    }

    @Override
    public synchronized ServiceDescriptor[] providedServices() {
        if (providedServiceDescriptors == null) {
            final var descriptors = new ServiceDescriptor[providedServices.size()];
            var i = 0;
            for (final var service : providedServices.values()) {
                descriptors[i++] = new ServiceDescriptor(
                    service.name(),
                    Stream.of(service.encodings())
                        .map(encoding -> InterfaceDescriptor
                            .getOrCreate(TransportDescriptor.HTTP, isSecured(), encoding))
                        .collect(Collectors.toList()));
            }
            providedServiceDescriptors = descriptors;
        }
        return providedServiceDescriptors.clone();
    }

    @Override
    public synchronized void provideService(final HttpService service) {
        Objects.requireNonNull(service, "Expected service");
        final var existingService = providedServices.putIfAbsent(service.basePath(), service);
        if (existingService != null) {
            if (existingService == service) {
                return;
            }
            throw new IllegalStateException("Base path \"" +
                service.basePath() + "\" already in use by  \"" +
                existingService.name() + "\"; cannot provide \"" +
                service.name() + "\"");
        }
        providedServiceDescriptors = null; // Force recreation.
    }

    @Override
    public synchronized void dismissService(final HttpService service) {
        if (providedServices.remove(service.basePath()) != null) {
            providedServiceDescriptors = null; // Force recreation.
        }
    }

    @Override
    public synchronized void dismissAllServices() {
        providedServices.clear();
        providedServiceDescriptors = null; // Force recreation.
    }

    private Future<HttpServiceResponse> handle(final HttpServiceRequest request) {
        final var response = new HttpServiceResponse(request.version());
        for (final var entry : providedServices.entrySet()) {
            if (request.path().startsWith(entry.getKey())) {
                final var name = entry.getValue().name();
                return entry.getValue()
                    .handle(request, response)
                    .map(ignored -> response)
                    .mapCatch(throwable -> {
                        // TODO: Log properly.
                        System.err.println("HTTP service \"" + name + "\" never handled:");
                        throwable.printStackTrace();

                        return response
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .headers(new HttpHeaders())
                            .body(new byte[0]);
                    });
            }
        }
        // TODO: Allow user to set the function that handles this outcome.
        return Future.success(response
            .status(HttpStatus.NOT_FOUND)
            .headers(new HttpHeaders())
            .body(new byte[0]));
    }

    @Override
    public Future<?> serve() {
        try {
            SslContext sslContext = null;
            if (isSecured()) {
                final var keyStore = keyStore();
                sslContext = SslContextBuilder
                    .forServer(keyStore.privateKey(), keyStore.certificateChain())
                    .trustManager(trustStore().certificates())
                    .clientAuth(ClientAuth.REQUIRE)
                    .startTls(false)
                    .build();
            }

            final var channelFuture = NettyBootstraps
                .createServerBootstrapUsing((NettyScheduler) scheduler())
                .handler(new LoggingHandler(LogLevels.toNettyLogLevel(logLevel())))
                .childHandler(new NettyHttpServiceConnectionInitializer(this::handle, logLevel(), sslContext))
                .bind(super.localAddress(), super.localPort());

            final var future0 = new AtomicReference<>(channelFuture);
            return new Future<>() {
                @Override
                public void onResult(final Consumer<Result<Object>> consumer) {
                    channelFuture.addListener(future1 -> {
                        Throwable err;
                        error:
                        {
                            if (future0.get() == null) {
                                err = new CancellationException();
                                break error;
                            }
                            if (!future1.isSuccess()) {
                                err = future1.cause();
                                break error;
                            }
                            final var channelFuture = (ChannelFuture) future1;

                            final var channel = channelFuture.channel();
                            localSocketAddress.set((InetSocketAddress) channel.localAddress());

                            final var closeFuture = channel.closeFuture();
                            final var cancelFuture = future0.getAndSet(closeFuture);
                            if (cancelFuture == null) {
                                closeFuture.cancel(true);
                                err = new CancellationException();
                                break error;
                            }

                            closeFuture.addListener(future2 ->
                                consumer.accept(future2.isSuccess()
                                    ? Result.success(null)
                                    : Result.failure(future2.cause())));
                            return;
                        }
                        consumer.accept(Result.failure(err));
                    });
                }

                @Override
                public void cancel(final boolean mayInterruptIfRunning) {
                    final var channelFuture = future0.getAndSet(null);
                    if (channelFuture != null) {
                        channelFuture.cancel(mayInterruptIfRunning);
                    }
                }
            };
        }
        catch (final Throwable throwable) {
            return Future.failure(throwable);
        }
    }

    public static class Builder extends ArrowheadSystem.Builder<Builder, HttpArrowheadSystem> {
        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public HttpArrowheadSystem build() {
            return new HttpArrowheadSystem(this);
        }
    }

}
