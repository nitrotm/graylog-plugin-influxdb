package org.tmsrv.graylog.influxdb;

import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.Version;

import java.net.URI;
import java.util.Collections;
import java.util.Set;


public class InfluxOutputMetaData implements PluginMetaData {
    @Override
    public String getUniqueId() {
        return InfluxOutput.class.getCanonicalName();
    }

    @Override
    public String getName() {
        return "InfluxDB Output Plugin";
    }

    @Override
    public String getAuthor() {
        return "Antony Ducommun <nitro@tmsrv.org>";
    }

    @Override
    public URI getURL() {
        return URI.create("https://github.com/nitrotm/graylog-plugin-influx");
    }

    @Override
    public Version getVersion() {
        return new Version(1, 0, 0);
    }

    @Override
    public String getDescription() {
        return "Graylog plugin that writes metrics to an InfluxDB instance.";
    }

    @Override
    public Version getRequiredVersion() {
        return new Version(2, 0, 0);
    }

    @Override
    public Set<ServerStatus.Capability> getRequiredCapabilities() {
        return EnumSet.of(ServerStatus.Capability.SERVER);
    }
}
