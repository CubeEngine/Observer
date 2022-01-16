package org.cubeengine.plugin.observer;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.plugin.PluginContainer;

import java.util.concurrent.ThreadFactory;

public class ObserverThreadFactory implements ThreadFactory {

    private final ThreadGroup threadGroup;

    public ObserverThreadFactory(PluginContainer plugin) {
        this.threadGroup = new ThreadGroup(plugin.metadata().id());
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        return new Thread(threadGroup, r);
    }
}
