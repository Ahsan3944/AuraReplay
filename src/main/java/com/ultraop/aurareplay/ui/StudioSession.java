package com.ultraop.aurareplay.ui;

import com.ultraop.aurareplay.actor.ActorId;

import java.util.UUID;

/** Viewer-local Studio navigation and selection state. */
public final class StudioSession {
    public enum Page { ROOT, CATEGORY, ACTORS }

    private final UUID viewerId;
    private Page page = Page.ROOT;
    private String category;
    private ActorId selectedActor;

    public StudioSession(UUID viewerId) { this.viewerId = viewerId; }
    public UUID viewerId() { return viewerId; }
    public Page page() { return page; }
    public String category() { return category; }
    public ActorId selectedActor() { return selectedActor; }

    public void root() { page = Page.ROOT; category = null; }
    public void category(String category) { page = Page.CATEGORY; this.category = category; }
    public void actors() { page = Page.ACTORS; category = "Actors"; }
    public void selectActor(ActorId actorId) { selectedActor = actorId; }
}
