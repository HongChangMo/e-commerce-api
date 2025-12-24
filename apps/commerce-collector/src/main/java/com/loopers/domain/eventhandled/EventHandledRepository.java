package com.loopers.domain.eventhandled;

import java.util.List;

public interface EventHandledRepository {
    boolean existsByEventId(String eventId);
    EventHandled save(EventHandled eventHandled);
    void saveAll(List<EventHandled> eventHandledList);

    List<EventHandled> findByEventId(String eventId);
}
