package net.hypixel.nerdbot.generator.parser.event;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public final class HoverEvent {

    private final @NotNull Action action;
    private final @NotNull String value;

    public @NotNull JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("action", this.getAction().toString());
        object.addProperty("value", this.getValue());
        return object;
    }

    public static @NotNull HoverEvent fromJson(JsonObject object) {
        String action = object.getAsJsonPrimitive("action").getAsString();
        String value = object.getAsJsonPrimitive("value").getAsString();
        return new HoverEvent(Action.valueOf(action), value);
    }

    public enum Action {
        SHOW_TEXT,
        SHOW_ITEM,
        SHOW_ENTITY;

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }
    }
}
