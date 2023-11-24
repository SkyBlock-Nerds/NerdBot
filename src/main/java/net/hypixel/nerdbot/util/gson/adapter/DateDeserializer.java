package net.hypixel.nerdbot.util.gson.adapter;

import com.google.gson.*;
import net.hypixel.nerdbot.util.TimeUtil;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.util.Date;

public class DateDeserializer implements JsonDeserializer<Date> {

    @Override
    public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        JsonElement dateElement = jsonObject.get("$date");

        if (dateElement != null && dateElement.isJsonPrimitive()) {
            String dateString = dateElement.getAsString();

            try {
                return TimeUtil.GLOBAL_DATE_TIME_FORMAT.parse(dateString);
            } catch (ParseException e) {
                throw new JsonParseException("Error parsing date: " + dateString, e);
            }
        } else {
            throw new JsonParseException("Invalid date format");
        }
    }
}