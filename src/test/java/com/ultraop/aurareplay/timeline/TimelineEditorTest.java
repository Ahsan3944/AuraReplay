package com.ultraop.aurareplay.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimelineEditorTest {
    @Test
    void editsAreUndoableAndRedoable() {
        Timeline timeline = new Timeline(100);
        TimelineEditor editor = new TimelineEditor();
        editor.setSpeed(timeline, 0.5);
        assertEquals(0.5, timeline.playbackSpeed());
        assertTrue(editor.undo(timeline));
        assertEquals(1.0, timeline.playbackSpeed());
        assertTrue(editor.redo(timeline));
        assertEquals(0.5, timeline.playbackSpeed());
    }

    @Test
    void timeStretchPreservesObjects() {
        Timeline timeline = new Timeline(100);
        timeline.addMarker(new TimelineMarker("m", 20, "mark"));
        timeline.addKeyframe(new TimelineKeyframe(40, "camera.fov", "70"));
        TimelineEditor editor = new TimelineEditor();
        editor.timeStretch(timeline, 2.0);
        assertEquals(200, timeline.durationTicks());
        assertEquals(40, timeline.markers().get(0).tick());
        assertEquals(80, timeline.keyframes().get(0).tick());
    }
}
