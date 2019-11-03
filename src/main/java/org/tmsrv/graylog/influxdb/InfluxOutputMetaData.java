package org.tmsrv.graylog.influxdb;

import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.Version;

import java.net.URI;
import java.util.EnumSet;
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
        return Version.fromClasspathProperties(
          InfluxOutputMetaData.class,
          "META-INF/maven/org.tmsrv.graylog/graylog-plugin-influxdb/pom.properties",
          "version",
          null,
          null,
          Version.from(1, 0, 0, "SNAPSHOT")
        );
    }

    @Override
    public String getDescription() {
        return "Graylog plugin that writes metrics to an InfluxDB instance.";
    }

    @Override
    public Version getRequiredVersion() {
        return Version.from(2, 0, 0);
    }

    @Override
    public Set<ServerStatus.Capability> getRequiredCapabilities() {
        return EnumSet.of(ServerStatus.Capability.SERVER);
    }
}
