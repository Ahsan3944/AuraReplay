package com.ultraop.aurareplay.timeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-scene timeline editor and history registry. */
public final class TimelineEditService {
    private final Map<String, TimelineEditor> editors = new ConcurrentHashMap<>();

    public TimelineEditor editor(String sceneName) {
        return editors.computeIfAbsent(sceneName.trim().toLowerCase(java.util.Locale.ROOT), ignored -> new TimelineEditor());
    }

    public void forget(String sceneName) {
        editors.remove(sceneName.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public void clear() { editors.clear(); }
}
