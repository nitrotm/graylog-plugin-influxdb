package org.tmsrv.graylog.influxdb;

import org.graylog2.plugin.PluginModule;


public class InfluxOutputModule extends PluginModule {
    @Override
    protected void configure() {
        addMessageOutput(InfluxOutput.class, InfluxOutput.Factory.class);
    }
}
