package org.tmsrv.graylog.influxdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.google.inject.assistedinject.Assisted;

import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.streams.Stream;

import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;

import org.joda.time.DateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class InfluxOutput implements MessageOutput {
    private static final String CK_INFLUX_URL = "influx_url";
    private static final String CK_INFLUX_USERNAME = "influx_user";
    private static final String CK_INFLUX_PASSWORD = "influx_password";
    private static final String CK_INFLUX_DATABASE = "influx_database";
    private static final String CK_INFLUX_SERIE = "influx_serie";
    private static final String CK_INFLUX_TAGS = "influx_tags";
    private static final String CK_INFLUX_FIELDS = "influx_fields";

    private static final Logger LOG = LoggerFactory.getLogger(InfluxOutput.class);

    private Configuration configuration;
    private Set<String> tags = new HashSet<String>();
    private Map<String, String> fields = new HashMap<String, String>();

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private InfluxDB influxDB;


    @Inject
    public InfluxOutput(@Assisted Configuration configuration) {
      this.configuration = configuration;

      String url = configuration.getString(CK_INFLUX_URL);
      String database = configuration.getString(CK_INFLUX_DATABASE);
      String username = configuration.getString(CK_INFLUX_USERNAME);
      String password = configuration.getString(CK_INFLUX_PASSWORD);

      LOG.debug("Starting InfluxDB output (" + url + "/" + database + ")");

      this.influxDB = InfluxDBFactory.connect(url, username, password);
      this.influxDB.query(new Query("CREATE DATABASE " + database));
      this.influxDB.setDatabase(database);
      this.influxDB.enableBatch(BatchOptions.DEFAULTS);

      for (String item : configuration.getString(CK_INFLUX_TAGS).split(",")) {
        item = item.trim();
        if (item.length() == 0) {
          continue;
        }
        this.tags.add(item);
      }
      for (String item : configuration.getString(CK_INFLUX_FIELDS).split(",")) {
        item = item.trim();
        if (item.length() == 0) {
          continue;
        }
        if (item.indexOf('=') > 0) {
          String key = item.substring(0, item.indexOf('=')).trim();
          String value = item.substring(item.indexOf('=') + 1).trim();

          this.fields.put(key, value);
        } else {
          this.fields.put(item, null);
        }
      }
      this.isRunning.set(true);

      LOG.info("InfluxDB output started (" + url + "/" + database + ")");
    }


    @Override
    public boolean isRunning() {
      return this.isRunning.get();
    }

    @Override
    public void write(Message message) throws Exception {
      if (!this.isRunning.get()) {
        return;
      }

      Point pt = this.buildPoint(message);

      if (pt != null) {
        this.influxDB.write(
          BatchPoints.builder()
            .point(pt)
            .build()
        );
      }
    }

    @Override
    public void write(List<Message> messages) throws Exception {
      if (!this.isRunning.get()) {
        return;
      }

      List<Point> pts = new ArrayList<Point>(messages.size());

      for (Message message : messages) {
        Point pt = this.buildPoint(message);

        if (pt != null) {
          pts.add(pt);
        }
      }
      if (pts.size() > 0) {
        this.influxDB.write(
          BatchPoints.builder()
            .points(pts)
            .build()
        );
      }
    }

    @Override
    public void stop() {
      String url = configuration.getString(CK_INFLUX_URL);
      String database = configuration.getString(CK_INFLUX_DATABASE);

      LOG.debug("Stopping InfluxDB output (" + url + "/" + database + ")");

      this.isRunning.set(false);
      this.influxDB.close();

      LOG.info("InfluxDB output stopped (" + url + "/" + database + ")");
    }

    private Point buildPoint(Message message) {
      Point.Builder builder = Point.measurement(configuration.getString(CK_INFLUX_SERIE))
        .time(message.getTimestamp().getMillis(), TimeUnit.MILLISECONDS)
        .tag("source", message.getSource());
      Map<String, Object> fields = message.getFields();

      for (String key : this.tags) {
        Object value = fields.get(key);

        if (value == null) {
          return null;
        }
        builder.tag(key, String.valueOf(value));
      }
      for (Map.Entry<String, String> entry : this.fields.entrySet()) {
        String key = entry.getKey();
        Object value = fields.get(key);

        if (value == null) {
          return null;
        }
        if (entry.getValue() != null) {
          builder.addField(key, String.valueOf(value).equals(entry.getValue()) ? 1 : 0);
        } else {
          if (value instanceof Boolean) {
              builder.addField(key, ((Boolean)value).booleanValue());
          } else if (value instanceof Number) {
              builder.addField(key, (Number)value);
          } else {
              builder.addField(key, String.valueOf(value));
          }
        }
      }

      return builder.build();
    }

    public interface Factory extends MessageOutput.Factory<InfluxOutput> {
        // @Override
        InfluxOutput create(Configuration configuration);

        // @Override
        Config getConfig();

        // @Override
        Descriptor getDescriptor();
    }

    public static class Config extends MessageOutput.Config {
        @Override
        public ConfigurationRequest getRequestedConfiguration() {
            final ConfigurationRequest configurationRequest = new ConfigurationRequest();

            configurationRequest.addField(new TextField(
                            CK_INFLUX_URL, "Server URL", "http://localhost:8086",
                            "URL of your InfluxDB instance",
                            ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_INFLUX_USERNAME, "Username", "root",
                            "Username to connect to database",
                            ConfigurationField.Optional.OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_INFLUX_PASSWORD, "Password", "root",
                            "Password to connect to database",
                            ConfigurationField.Optional.OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_INFLUX_DATABASE, "Database", "graylog",
                            "Database name",
                            ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_INFLUX_SERIE, "Serie", "graylog",
                            "Serie name",
                            ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_INFLUX_TAGS, "Extract tags", "",
                            "Source fields to use as Influx tags (comma-separated names).",
                            ConfigurationField.Optional.OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_INFLUX_FIELDS, "Extract fields", "",
                            "Source fields to use as Influx value (comma-separated names). If the field is not a number, it can be converted to 0 or 1 by matching its value (eg. text_field=myvalue).",
                            ConfigurationField.Optional.NOT_OPTIONAL)
            );
            return configurationRequest;
        }
    }

    public static class Descriptor extends MessageOutput.Descriptor {
        public Descriptor() {
            super("InfluxDB Output", false, "", "An output plugin sending metrics to InfluxDB");
        }
    }
}
