package se.arkalix.core.plugin;

import se.arkalix.ArSystem;
import se.arkalix.core.plugin.dto.EventBuilder;
import se.arkalix.core.plugin.dto.EventDto;
import se.arkalix.core.plugin.dto.Instants;
import se.arkalix.core.plugin.dto.SystemDetails;
import se.arkalix.util.concurrent.Future;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an Arrowhead event publishing service.
 */
@SuppressWarnings("unused")
public interface ArEventPublish {
    /**
     * Publishes given {@code event}.
     *
     * @param event Event to publish.
     * @return {@code Future} completed when the publishing attempt is known to
     * have succeeded or failed.
     */
    Future<?> publish(EventDto event);

    /**
     * Publishes given arguments as an {@link se.arkalix.core.plugin.dto.Event}.
     *
     * @param topic     Category of event.
     * @param publisher System publishing the event.
     * @param data      Arbitrary string data associated with event.
     * @return {@code Future} completed when the publishing attempt is known to
     * have succeeded or failed.
     */
    default Future<?> publish(final String topic, final ArSystem publisher, final String data) {
        return publish(topic, publisher, null, data);
    }

    /**
     * Publishes given arguments as an {@link se.arkalix.core.plugin.dto.Event}.
     *
     * @param topic     Category of event.
     * @param publisher System publishing the event.
     * @param metadata  Arbitrary metadata associated with event.
     * @param data      Arbitrary string data associated with event.
     * @return {@code Future} completed when the publishing attempt is known to
     * have succeeded or failed.
     */
    default Future<?> publish(
        final String topic,
        final ArSystem publisher,
        final Map<String, String> metadata,
        final String data)
    {
        return publish(new EventBuilder()
            .topic(topic)
            .publisher(SystemDetails.from(publisher))
            .metadata(metadata)
            .data(data)
            .createdAt(Instants.toAitiaDateTimeString(Instant.now()))
            .build());
    }
}
