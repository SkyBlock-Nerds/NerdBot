package net.hypixel.nerdbot.util.gson.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateDeserializer implements JsonDeserializer<Date> {

    @Override
    public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        JsonElement dateElement = jsonObject.get("$date");

        if (dateElement != null && dateElement.isJsonPrimitive()) {
            String dateString = dateElement.getAsString();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            try {
                return dateFormat.parse(dateString);
            } catch (ParseException exception) {
                throw new JsonParseException("Error parsing date: " + dateString, exception);
            }
        } else {
            throw new JsonParseException("Invalid date format");
        }
    }
}