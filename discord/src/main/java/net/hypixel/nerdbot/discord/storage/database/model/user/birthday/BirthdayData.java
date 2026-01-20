package net.hypixel.nerdbot.discord.storage.database.model.user.birthday;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.core.json.adapter.EpochMillisAdapter;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;

@Setter
@Getter
public class BirthdayData {

    @JsonAdapter(EpochMillisAdapter.class)
    @SerializedName(value = "birthdayTimestamp", alternate = {"birthday"})
    private long birthdayTimestamp;
    private boolean shouldAnnounceAge;
    private transient Timer timer;

    public BirthdayData() {
        this(-1L, false, null);
    }

    public BirthdayData(long birthdayTimestamp, boolean shouldAnnounceAge, Timer timer) {
        this.birthdayTimestamp = birthdayTimestamp;
        this.shouldAnnounceAge = shouldAnnounceAge;
        this.timer = timer;
    }

    public boolean isBirthdaySet() {
        return birthdayTimestamp > 0;
    }

    public Date getBirthday() {
        return getBirthdayDate();
    }

    public void setBirthday(Date birthday) {
        this.birthdayTimestamp = birthday != null ? birthday.getTime() : -1L;
    }

    public Date getBirthdayDate() {
        return isBirthdaySet() ? new Date(birthdayTimestamp) : null;
    }

    public long getBirthdayThisYearTimestamp() {
        if (!isBirthdaySet()) {
            return -1L;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(birthdayTimestamp);
        calendar.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR));

        long thisYear = calendar.getTimeInMillis();

        if (thisYear < System.currentTimeMillis()) {
            calendar.add(Calendar.YEAR, 1);
            thisYear = calendar.getTimeInMillis();
        }

        return thisYear;
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