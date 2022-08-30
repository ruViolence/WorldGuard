package com.sk89q.worldguard.bukkit.event.api;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class RegionAddedEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final ProtectedRegion region;
    private final Cause cause;
    private final Player who;
    private boolean cancel;

    public RegionAddedEvent(ProtectedRegion region, Cause cause, Player who) {
        super(true);
        this.region = region;
        this.cause = cause;
        this.who = who;
    }

    public ProtectedRegion getRegion() {
        return this.region;
    }

    public Cause getCause() {
        return this.cause;
    }

    public Player getPlayer() {
        return this.who;
    }

    @Override
    public boolean isCancelled() {
        return this.cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public enum Cause {
        CLAIM, DEFINE, REDEFINE
    }
}
