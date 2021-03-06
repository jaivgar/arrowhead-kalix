package se.arkalix.core.plugin.sr;

import se.arkalix.ArConsumer;
import se.arkalix.ArSystem;
import se.arkalix.core.plugin.ServiceDetails;
import se.arkalix.description.ServiceDescription;
import se.arkalix.query.ServiceQuery;
import se.arkalix.util.concurrent.Future;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Represents an Arrowhead service discovery service.
 */
@SuppressWarnings("unused")
public interface ArServiceDiscoveryService extends ArConsumer {
    /**
     * Queries registry for certain service definitions.
     *
     * @param query Description of what service definitions are desired.
     * @return Future completed with the results of the query, if no errors
     * occurred.
     */
    Future<ServiceQueryResultDto> query(ServiceQueryDto query);

    /**
     * Queries registry for certain service definitions.
     *
     * @param query Description of what service definitions are desired.
     * @return Future completed with the results of the query, if no errors
     * occurred.
     */
    default Future<Collection<ServiceDescription>> query(final ServiceQuery query) {
        return query(se.arkalix.core.plugin.sr.ServiceQuery.from(query))
            .map(result -> result.services().stream()
                .map(ServiceDetails::toServiceDescription)
                .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * Registers a service.
     *
     * @param registration Description of service.
     * @return Future completed when the registration attempt is known to have
     * succeeded or failed.
     */
    Future<?> register(ServiceRegistrationDto registration);

    /**
     * Registers a service.
     *
     * @param serviceDescription Description of service to register.
     * @return Future completed when the registration attempt is known to have
     * succeeded or failed.
     */
    default Future<?> register(final ServiceDescription serviceDescription) {
        return register(ServiceRegistration.from(serviceDescription));
    }

    /**
     * Unregisters a service that is currently registered.
     *
     * @param serviceName Name of service of existing entry.
     * @param systemName  Name of system of existing entry.
     * @param hostname    Address/hostname of existing entry.
     * @param port        Port number of existing entry.
     * @return Future completed when unregistration is known to have succeeded
     * or failed.
     */
    Future<?> unregister(String serviceName, String systemName, String hostname, int port);

    /**
     * Unregisters a service that is currently registered.
     *
     * @param serviceName Name of service of existing entry.
     * @param system      System of existing entry.
     * @return Future completed when unregistration is known to have succeeded
     * or failed.
     */
    default Future<?> unregister(final String serviceName, final ArSystem system) {
        return unregister(serviceName, system.name(), system.localSocketAddress().getHostString(), system.localPort());
    }
}
