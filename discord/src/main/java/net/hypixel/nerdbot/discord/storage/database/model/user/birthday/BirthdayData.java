package net.hypixel.nerdbot.discord.storage.database.model.user.birthday;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.core.json.adapter.EpochMillisAdapter;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimeZone;

@Setter
@Getter
public class BirthdayData {

    @JsonAdapter(EpochMillisAdapter.class)
    @SerializedName(value = "birthdayTimestamp", alternate = {"birthday"})
    private long birthdayTimestamp;
    private boolean shouldAnnounceAge;
    private String timezone;
    private transient Timer timer;

    public BirthdayData() {
        this(-1L, false, null, null);
    }

    public BirthdayData(long birthdayTimestamp, boolean shouldAnnounceAge, Timer timer, String timezone) {
        this.birthdayTimestamp = birthdayTimestamp;
        this.shouldAnnounceAge = shouldAnnounceAge;
        this.timer = timer;
        this.timezone = timezone;
    }

    public ZoneId getTimeZoneId() {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }

        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    public boolean isBirthdaySet() {
        return birthdayTimestamp > 0;
    }

    public Date getBirthday() {
        return getBirthdayDate();
    }

    public Date getBirthdayDate() {
        return isBirthdaySet() ? new Date(birthdayTimestamp) : null;
    }

    public void setBirthday(Date birthday) {
        this.birthdayTimestamp = birthday != null ? birthday.getTime() : -1L;
    }

    public long getBirthdayThisYearTimestamp() {
        if (!isBirthdaySet()) {
            return -1L;
        }

        ZoneId zoneId = getTimeZoneId();

        Calendar birthdayCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        birthdayCal.setTimeInMillis(birthdayTimestamp);
        int month = birthdayCal.get(Calendar.MONTH) + 1;
        int day = birthdayCal.get(Calendar.DAY_OF_MONTH);

        // Create this year's birthday at midnight in the user's timezone
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime birthdayThisYear = ZonedDateTime.of(
            now.getYear(), month, day, 0, 0, 0, 0, zoneId
        );

        // If the birthday has already passed this year, schedule for next year
        if (birthdayThisYear.isBefore(now) || birthdayThisYear.isEqual(now)) {
            birthdayThisYear = birthdayThisYear.plusYears(1);
        }

        return birthdayThisYear.toInstant().toEpochMilli();
    }

    public Date getBirthdayThisYear() {
        long thisYearTimestamp = getBirthdayThisYearTimestamp();
        return thisYearTimestamp > 0 ? new Date(thisYearTimestamp) : null;
    }

    public int getAge() {
        if (!isBirthdaySet()) {
            return 0;
        }

        long diff = System.currentTimeMillis() - birthdayTimestamp;
        return (int) (diff / (1000L * 60L * 60L * 24L * 365L));
    }
}