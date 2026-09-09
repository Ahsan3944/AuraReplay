package com.ultraop.aurareplay.scene;

import com.ultraop.aurareplay.timeline.TimelineEditService;
import com.ultraop.aurareplay.timeline.TimelineEditor;

/** Scene-facing access to non-destructive timeline editing and history. */
public final class SceneTimelineService {
    private final TimelineEditService edits = new TimelineEditService();

    public TimelineEditor editor(Scene scene) {
        return edits.editor(scene.name());
    }

    public void forget(Scene scene) { edits.forget(scene.name()); }
    public void clear() { edits.clear(); }
}
