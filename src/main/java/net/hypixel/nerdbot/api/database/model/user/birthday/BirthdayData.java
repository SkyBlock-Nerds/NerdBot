package net.hypixel.nerdbot.api.database.model.user.birthday;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;

@AllArgsConstructor
@Getter
@Setter
public class BirthdayData {

    private Date birthday;
    private boolean shouldAnnounceAge;
    private transient Timer timer;

    public BirthdayData() {
    }

    public Date getBirthdayThisYear() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(birthday);
        calendar.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR));

        return calendar.getTime();
    }

    public int getAge() {
        Date now = new Date();
        long diff = now.getTime() - birthday.getTime();
        return (int) (diff / (1000L * 60L * 60L * 24L * 365L));
    }

    public boolean isBirthdaySet() {
        return birthday != null;
    }
}
