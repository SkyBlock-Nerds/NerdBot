package net.hypixel.nerdbot.app.inject;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Marks the pool the {@code MemberQueue} runs members' record changes on. */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface MemberWork {
}
