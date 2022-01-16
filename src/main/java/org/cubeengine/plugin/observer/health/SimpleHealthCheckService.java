/*
 * This file is part of CubeEngine.
 * CubeEngine is licensed under the GNU General Public License Version 3.
 *
 * CubeEngine is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CubeEngine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CubeEngine.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.cubeengine.plugin.observer.health;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.logging.log4j.Logger;
import org.cubeengine.plugin.observer.web.FailureCallback;
import org.cubeengine.plugin.observer.web.SuccessCallback;
import org.cubeengine.plugin.observer.web.WebHandler;
import org.spongepowered.observer.healthcheck.AsyncHealthProbe;
import org.spongepowered.observer.healthcheck.HealthCheckCollection;
import org.spongepowered.observer.healthcheck.HealthState;
import org.spongepowered.observer.healthcheck.SyncHealthProbe;
import org.spongepowered.plugin.PluginContainer;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.cubeengine.plugin.observer.Util.sequence;

public class SimpleHealthCheckService implements WebHandler {

    private final ExecutorService syncExecutor;
    private final HealthCheckCollection collection;
    private final Logger logger;

    private final Gson gson = new Gson();

    public SimpleHealthCheckService(ExecutorService syncExecutor, HealthCheckCollection collection, Logger logger) {
        this.syncExecutor = syncExecutor;
        this.collection = collection;
        this.logger = logger;
    }

    public void handleRequest(SuccessCallback success, FailureCallback failure, FullHttpRequest request, QueryStringDecoder queryStringDecoder) {

        final Map<String, SyncHealthProbe> syncProbes = new HashMap<>();
        final Map<String, AsyncHealthProbe> asyncProbes = new HashMap<>();
        collection.probes().forEach((id, probe) -> {
            if (probe instanceof AsyncHealthProbe) {
                asyncProbes.put(id, (AsyncHealthProbe) probe);
            }
            else if (probe instanceof SyncHealthProbe) {
                syncProbes.put(id, (SyncHealthProbe) probe);
            }
            else {
                logger.warn("Encountered a health probe that is neither async nor sync with id '" + id + "': " + probe);
            }
        });

        sequence(asList(getSyncStates(syncProbes), getAsyncStates(asyncProbes))).whenComplete((results, t) -> {
            if (t != null) {
                failure.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR, t);
                return;
            }

            Map<String, HealthState> details = new HashMap<>();
            for (Map<String, HealthState> result : results) {
                details.putAll(result);
            }

            HealthState state = HealthState.HEALTHY;
            for (HealthState value : details.values()) {
                if (value.ordinal() > state.ordinal()) {
                    state = value;
                }
            }
            final HttpResponseStatus httpStatus = state != HealthState.HEALTHY ? HttpResponseStatus.SERVICE_UNAVAILABLE :  HttpResponseStatus.OK;
            final ByteBuf buffer = request.content().alloc().buffer();
            buffer.writeCharSequence(gson.toJson(new HealthResult(state, details)), StandardCharsets.UTF_8);
            final DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpStatus, buffer);
            response.headers().set("Content-Type", "application/json");
            success.succeed(response);
        });
    }

    private CompletableFuture<Map<String, HealthState>> getSyncStates(Map<String, SyncHealthProbe> syncProbes) {
        final Map<String, SyncHealthProbe> probes;
        synchronized (this) {
            if (syncProbes.isEmpty()) {
                return CompletableFuture.completedFuture(Collections.emptyMap());
            }
            probes = new HashMap<>(syncProbes);
        }

        return CompletableFuture.supplyAsync(() -> {
            Map<String, HealthState> result = new HashMap<>(probes.size());
            for (Map.Entry<String, SyncHealthProbe> entry : probes.entrySet()) {
                result.put(entry.getKey(), entry.getValue().probe());
            }
            return result;
        }, syncExecutor);
    }

    private CompletableFuture<Map<String, HealthState>> getAsyncStates(Map<String, AsyncHealthProbe> asyncProbes) {
        final Map<String, AsyncHealthProbe> probes;
        synchronized (this) {
            if (asyncProbes.isEmpty()) {
                return CompletableFuture.completedFuture(Collections.emptyMap());
            }
            probes = new HashMap<>(asyncProbes);
        }

        return sequence(probes.entrySet().stream().parallel().map(entry -> entry.getValue().probe().thenApply(r -> new ProbeResult(entry.getKey(), r))).collect(toList()))
            .thenApply(results -> {
                final Map<String, HealthState> result = new HashMap<>(results.size());
                for (ProbeResult pair : results) {
                    result.put(pair.getName(), pair.getState());
                }
                return result;
            });
    }

    private static String prefix(PluginContainer plugin) {
        return plugin.metadata().id() + ":";
    }

    private static final class ProbeResult {
        private final String name;
        private final HealthState state;

        public ProbeResult(String name, HealthState state) {
            this.name = name;
            this.state = state;
        }

        public String getName() {
            return name;
        }

        public HealthState getState() {
            return state;
        }
    }
}
