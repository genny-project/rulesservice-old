package life.genny.rulesservice;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public class DateTimeDeserializer implements JsonDeserializer<LocalDateTime> {

  @Override
  public LocalDateTime deserialize(JsonElement element, Type arg1, JsonDeserializationContext arg2) throws JsonParseException {
      String datetime = element.getAsString();

      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
      format.setTimeZone(TimeZone.getTimeZone("GMT"));

      try {
          Date result = format.parse(datetime);
          return result.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
      } catch (ParseException exp) {
          System.err.println("Failed to parse Date:"+ exp);
          return null;
      }
   }
}