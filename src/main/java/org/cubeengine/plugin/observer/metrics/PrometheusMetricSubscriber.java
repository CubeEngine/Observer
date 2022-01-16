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
package org.cubeengine.plugin.observer.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.SimpleCollector;
import io.prometheus.client.SimpleCollector.Builder;
import org.spongepowered.observer.metrics.MetricSubscriber;
import org.spongepowered.observer.metrics.meter.Metadata;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PrometheusMetricSubscriber implements MetricSubscriber {
    private final CollectorRegistry registry;

    private final ConcurrentMap<String[], Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String[], Gauge> gaugeCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String[], Histogram> timerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String[], Histogram> histogramCache = new ConcurrentHashMap<>();

    public PrometheusMetricSubscriber(CollectorRegistry registry) {
        this.registry = registry;
    }

    private <CC, C extends SimpleCollector<CC>, B extends Builder<B, C>> CC getLabeledCollector(Metadata metadata,
                                                                                                Supplier<B> builder,
                                                                                                ConcurrentMap<String[], C> cache,
                                                                                                Object[] labelValues) {
        return getLabeledCollector(metadata, builder, cache, labelValues, ignored -> {});
    }

    private <ChildT, CollectorT extends SimpleCollector<ChildT>, BuilderT extends Builder<BuilderT, CollectorT>> ChildT getLabeledCollector(Metadata metadata,
                                                                                                                                            Supplier<BuilderT> builder,
                                                                                                                                            ConcurrentMap<String[], CollectorT> cache,
                                                                                                                                            Object[] labelValues,
                                                                                                                                            Consumer<BuilderT> customizer) {
        final CollectorT collector = cache.computeIfAbsent(metadata.name, name -> {
            final BuilderT instance = builder.get();
            instance.name(String.join("_", name))
                    .help(metadata.help)
                    .labelNames(metadata.labelNames);
            customizer.accept(instance);
            return instance.register(registry);
        });

        String[] labelValueStrings = new String[labelValues.length];
        for (int i = 0; i < labelValues.length; i++) {
            labelValueStrings[i] = String.valueOf(labelValues[i]);
        }
        return collector.labels(labelValueStrings);
    }

    @Override
    public void onCounterIncrement(Metadata metadata, double incrementedBy, Object[] labelValues) {
        getLabeledCollector(metadata, Counter::build, counterCache, labelValues).inc(incrementedBy);
    }

    @Override
    public void onGaugeSet(Metadata metadata, double value, Object[] labelValues) {
        getLabeledCollector(metadata, Gauge::build, gaugeCache, labelValues).set(value);
    }

    @Override
    public void onTimerObserved(Metadata metadata, double seconds, Object[] labelValues) {
        getLabeledCollector(metadata, Histogram::build, timerCache, labelValues).observe(seconds);
    }

    @Override
    public void onHistogramObserved(Metadata metadata, double[] buckets, double value, Object[] labelValues) {
        getLabeledCollector(metadata, Histogram::build, histogramCache, labelValues, b -> b.buckets(buckets)).observe(value);
    }
}
