/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.bukkit.listener;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.ruviolence.reaper.event.entity.EntityMoveEvent;
import com.sk89q.worldguard.bukkit.RegionQuery;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.util.Locations;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EntityMoveListener implements Listener {

    private final WorldGuardPlugin plugin;
    private final LoadingCache<Entity, Session> cache = Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build(Session::new);
    private final List<Handler> handlers = new ArrayList<>();

    public EntityMoveListener(WorldGuardPlugin plugin) {
        this.plugin = plugin;
        this.handlers.add((toSet, session) -> toSet.testState(null, DefaultFlag.MOB_ENTRY));
        this.handlers.add((toSet, session) -> {
            boolean currentValue = toSet.testState(null, DefaultFlag.MOB_EXIT);
            boolean allowed = true;

            if (currentValue != session.lastExitValue) {
                allowed = !currentValue;
            }

            if (allowed) {
                session.lastExitValue = currentValue;
            }

            return allowed;
        });
    }

    public void registerEvents() {
        if (plugin.getGlobalStateManager().useEntityMove) {
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvents(this, plugin);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityMove(EntityMoveEvent event) {
        if (!event.isPerBlockMove()) return;

        final Entity entity = event.getEntity();

        if (!(entity instanceof LivingEntity) && !(entity instanceof Vehicle)) return;
        if (!plugin.getGlobalStateManager().get(entity.getWorld()).useRegions) return;

        Session session = cache.get(entity);

        if (session.testMoveTo(event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!Locations.isDifferentBlock(event.getFrom(), event.getTo())) return;

        final Player player = event.getPlayer();
        final Entity vehicle = player.getVehicle();

        if (vehicle == null) return;
        if (!plugin.getGlobalStateManager().get(player.getWorld()).useRegions) return;

        Session session = cache.get(vehicle);

        if (session.testMoveTo(event.getTo())) {
            vehicle.eject();
        }
    }

    private class Session {
        private boolean lastExitValue;

        public Session(Entity entity) {
            RegionQuery query = plugin.getRegionContainer().createQuery();
            Location location = entity.getLocation();
            ApplicableRegionSet set = query.getApplicableRegions(location);

            lastExitValue = set.testState(null, DefaultFlag.MOB_EXIT);
        }

        /**
         * Test movement to the given location.
         *
         * <p>If a true is returned, the move should be cancelled.</p>
         *
         * @param to The new location
         * @return The result
         */
        public boolean testMoveTo(Location to) {
            RegionQuery query = plugin.getRegionContainer().createQuery();
            ApplicableRegionSet toSet = query.getApplicableRegions(to);

            for (Handler handler : handlers) {
                if (!handler.test(toSet, this)) {
                    return true;
                }
            }

            return false;
        }
    }

    private interface Handler {
        boolean test(ApplicableRegionSet toSet, Session session);
    }

}
