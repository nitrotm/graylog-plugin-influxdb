package org.tmsrv.graylog.influxdb;

import org.graylog2.plugin.Plugin;
import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.PluginModule;

import java.util.Collection;
import java.util.Collections;


public class InfluxOutputPlugin implements Plugin {
    @Override
    public PluginMetaData metadata() {
        return new InfluxOutputMetaData();
    }

    @Override
    public Collection<PluginModule> modules () {
        return Collections.<PluginModule>singletonList(new InfluxOutputModule());
    }
}
