package org.tmsrv.graylog.influxdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import java.util.regex.Pattern;

import javax.inject.Inject;

import com.google.inject.assistedinject.Assisted;

import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.ListField;
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
    private static final String CK_INFLUX_MEASUREMENT = "influx_measurement";
    private static final String CK_INFLUX_FILTERS = "influx_filters";
    private static final String CK_INFLUX_TAGS = "influx_tags";
    private static final String CK_INFLUX_FIELDS = "influx_fields";

    private static final Logger LOG = LoggerFactory.getLogger(InfluxOutput.class);

    private Configuration configuration;
    private List<FieldMatcher> matchers = new ArrayList<FieldMatcher>();
    private Set<String> tags = new HashSet<String>();
    private List<FieldMapper> mappers = new ArrayList<FieldMapper>();

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

      for (String item : configuration.getList(CK_INFLUX_FILTERS)) {
        this.matchers.add(this.buildMatcher(item));
      }
      for (String item : configuration.getList(CK_INFLUX_TAGS)) {
        this.tags.add(item.trim());
      }
      for (String item : configuration.getList(CK_INFLUX_FIELDS)) {
        this.mappers.add(this.buildMapper(item));
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
      Point.Builder builder = Point.measurement(configuration.getString(CK_INFLUX_MEASUREMENT))
        .time(message.getTimestamp().getMillis(), TimeUnit.MILLISECONDS)
        .tag("source", message.getSource());
      Map<String, Object> fields = message.getFields();

      for (FieldMatcher matcher : this.matchers) {
        String key = matcher.field();

        if (!matcher.match(fields.get(key))) {
          return null;
        }
      }
      for (String key : this.tags) {
        Object value = fields.get(key);

        if (value != null) {
          builder.tag(key, String.valueOf(value));
        }
      }
      for (FieldMapper mapper : this.mappers) {
        String key = mapper.field();
        Object value = mapper.map(fields.get(key));

        if (value instanceof Boolean) {
            builder.addField(key, ((Boolean)value).booleanValue());
        } else if (value instanceof Number) {
            builder.addField(key, (Number)value);
        } else if (value != null) {
            builder.addField(key, String.valueOf(value));
        }
      }
      return builder.build();
    }

    private FieldMatcher buildMatcher(String source) {
      source = source.trim();
      if (source.length() == 0) {
        throw new IllegalArgumentException("Matcher source is empty");
      }
      if (source.indexOf("!=") > 0) {
        String key = source.substring(0, source.indexOf("!=")).trim();
        String value = source.substring(source.indexOf("!=") + 2).trim();

        return new StringFieldMatcher(key, true, value, true);
      }
      if (source.indexOf('=') > 0) {
        String key = source.substring(0, source.indexOf('=')).trim();
        String value = source.substring(source.indexOf('=') + 1).trim();

        return new StringFieldMatcher(key, false, value, true);
      }
      if (source.indexOf("!~") > 0) {
        String key = source.substring(0, source.indexOf("!~")).trim();
        String value = source.substring(source.indexOf("!~") + 2).trim();

        return new RegexFieldMatcher(key, true, value, 0);
      }
      if (source.indexOf('~') > 0) {
        String key = source.substring(0, source.indexOf('~')).trim();
        String value = source.substring(source.indexOf('~') + 1).trim();

        return new RegexFieldMatcher(key, false, value, 0);
      }
      if (source.startsWith("!")) {
        return new ConstantFieldMatcher(source.substring(1), true);
      }
      return new ConstantFieldMatcher(source, false);
    }

    private FieldMapper buildMapper(String source) {
      source = source.trim();
      if (source.length() == 0) {
        throw new IllegalArgumentException("Mapper source is empty");
      }
      if (source.indexOf('=') > 0 || source.indexOf('~') > 0) {
        try {
          return new BooleanFieldMapper(this.buildMatcher(source));
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Boolean mapper error (" + e + ")", e);
        }
      }
      return new IdentityFieldMapper(source);
    }

    private static interface FieldMatcher {
      String field();
      boolean match(Object value);
    }

    private static abstract class AbstractFieldMatcher implements FieldMatcher {
      private final String _field;
      private final boolean _negate;

      AbstractFieldMatcher(String field, boolean negate) {
        this._field = field;
        this._negate = negate;
      }

      public String field() {
        return this._field;
      }

      public final boolean match(Object value) {
        return this._negate ? !this.matchImpl(value) : this.matchImpl(value);
      }

      abstract boolean matchImpl(Object value);
    }

    private static class ConstantFieldMatcher extends AbstractFieldMatcher {
      ConstantFieldMatcher(String field, boolean negate) {
        super(field, negate);
      }

      boolean matchImpl(Object value) {
        return value != null;
      }
    }

    private static class StringFieldMatcher extends AbstractFieldMatcher {
      private final String _value;
      private final boolean _caseSensitive;

      StringFieldMatcher(String field, boolean negate, String value, boolean caseSensitive) {
        super(field, negate);
        this._value = value;
        this._caseSensitive = caseSensitive;
      }

      boolean matchImpl(Object value) {
        if (value == null) {
          return false;
        }
        return this._caseSensitive ? this._value.equals(String.valueOf(value)) : this._value.equalsIgnoreCase(String.valueOf(value));
      }
    }

    private static class RegexFieldMatcher extends AbstractFieldMatcher {
      private final Pattern _regex;

      RegexFieldMatcher(String field, boolean negate, String regex, int flags) {
        super(field, negate);
        this._regex = Pattern.compile(regex, flags);
      }

      boolean matchImpl(Object value) {
        if (value == null) {
          return false;
        }
        return this._regex.matcher(String.valueOf(value)).matches();
      }
    }

    private static interface FieldMapper {
      String field();
      Object map(Object value);
    }

    private static class IdentityFieldMapper implements FieldMapper {
      private final String _field;

      IdentityFieldMapper(String field) {
        this._field = field;
      }

      public String field() {
        return this._field;
      }

      public Object map(Object value) {
        return value;
      }
    }

    private static class BooleanFieldMapper implements FieldMapper {
      private final FieldMatcher _matcher;

      BooleanFieldMapper(FieldMatcher matcher) {
        this._matcher = matcher;
      }

      public String field() {
        return this._matcher.field();
      }

      public Object map(Object value) {
        return this._matcher.match(value) ? 1 : 0;
      }
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
                            CK_INFLUX_USERNAME, "Username", "",
                            "Username to connect to database",
                            ConfigurationField.Optional.OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_INFLUX_PASSWORD, "Password", "",
                            "Password to connect to database",
                            ConfigurationField.Optional.OPTIONAL,
                            TextField.Attribute.IS_PASSWORD)
            );

            configurationRequest.addField(new TextField(
                            CK_INFLUX_DATABASE, "Database", "graylog",
                            "Database name",
                            ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_INFLUX_MEASUREMENT, "Measurement", "graylog",
                            "Measurement name",
                            ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new ListField(
                            CK_INFLUX_FILTERS, "Filters", Collections.emptyList(), Collections.emptyMap(),
                            "Filters on source fields (name and optional value). A name can be prefix by ! to negate match. A value to match can be optionally given (eg. my_field=myvalue or my_field~myregexp).",
                            ConfigurationField.Optional.OPTIONAL,
                            ListField.Attribute.ALLOW_CREATE)
            );

            configurationRequest.addField(new ListField(
                            CK_INFLUX_TAGS, "Extract tags", Collections.emptyList(), Collections.emptyMap(),
                            "Source fields to use as Influx tags (name).",
                            ConfigurationField.Optional.OPTIONAL,
                            ListField.Attribute.ALLOW_CREATE)
            );

            configurationRequest.addField(new ListField(
                            CK_INFLUX_FIELDS, "Extract fields", Collections.emptyList(), Collections.emptyMap(),
                            "Source fields to use as Influx value (name and optional value). If the field is not a number, it can be converted to 0 or 1 by matching its value (eg. text_field=myvalue or text_field~myregexp).",
                            ConfigurationField.Optional.NOT_OPTIONAL,
                            ListField.Attribute.ALLOW_CREATE)
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
