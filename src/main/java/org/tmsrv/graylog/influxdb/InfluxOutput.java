package org.tmsrv.graylog.influxdb;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.influxdb.dto.Point;

import org.joda.time.DateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class InfluxOutput implements MessageOutput {
    private static final String CK_INFLUX_URL = "influx_url";
    private static final String CK_INFLUX_USERNAME = "influx_user";
    private static final String CK_INFLUX_PASSWORD = "influx_password";
    private static final String CK_INFLUX_DATABASE = "influx_database";
    private static final String CK_INFLUX_SERIE = "influx_serie";

    private static final Logger LOG = LoggerFactory.getLogger(InfluxOutput.class);

    private Configuration configuration;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private InfluxDB influxDB;


    @Inject
    public InfluxOutput(@Assisted Configuration configuration) throws HttpOutputException {
      this.configuration = configuration;

      String url = configuration.getString(CK_INFLUX_URL);
      String username = configuration.getString(CK_INFLUX_USERNAME);
      String password = configuration.getString(CK_INFLUX_PASSWORD);
      String database = configuration.getString(CK_INFLUX_DATABASE);

      LOG.info("Starting InfluxDB output (" + url + "/" + database + ")");

      this.influxDB = InfluxDBFactory.connect(url, username, password);
      this.influxDB.setDatabase(database);
      this.influxDB.enableBatch(BatchOptions.DEFAULTS);
    }


    @Override
    public boolean isRunning() {
      return false;
    }

    @Override
    public void write(Message message) throws Exception {
      if (this.isRunning.get()) {
        this.influxDB.write(
          Point.measurement(configuration.getString(CK_INFLUX_SERIE))
            .time(message.getTimestamp().getMillis(), TimeUnit.MILLISECONDS)
            .addField("idle", 90L)
            .addField("user", 9L)
            .addField("system", 1L)
            .build()
        );
      }
    }

    @Override
    public void write(List<Message> messages) throws Exception {
      for (Message message : messages) {
        write(message);
      }
    }

    @Override
    public void stop() {
      LOG.info("Stopping InfluxDB output");

      this.isRunning.set(false);
      this.influxDB.close();
    }

    public interface Factory extends MessageOutput.Factory<InfluxOutput> {
        @Override
        InfluxOutput create(Configuration configuration);

        @Override
        Config getConfig();

        @Override
        Descriptor getDescriptor();
    }

    public static class Config extends MessageOutput.Config {
        @Override
        public ConfigurationRequest getRequestedConfiguration() {
            final ConfigurationRequest configurationRequest = new ConfigurationRequest();

            configurationRequest.addField(new TextField(
                            CK_INFLUX_URL, "Server URL", "http://localhost:8086",
                            "URL of your InfluxDB instance",
                            ConfigurationField.Optional.OPTIONAL)
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
                            ConfigurationField.Optional.OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_INFLUX_SERIE, "Serie", "graylog",
                            "Serie name",
                            ConfigurationField.Optional.OPTIONAL)
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
