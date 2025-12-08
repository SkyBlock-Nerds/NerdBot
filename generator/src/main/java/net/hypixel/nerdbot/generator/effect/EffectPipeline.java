package net.hypixel.nerdbot.generator.effect;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pipeline for executing multiple effects in sequence.
 * Applies effects in the priority they are defined.
 */
@Slf4j
public class EffectPipeline {

    private final List<ImageEffect> effects;

    private EffectPipeline(List<ImageEffect> effects) {
        this.effects = new ArrayList<>(effects);
        // Sort effects by priority (lower number = earlier execution)
        this.effects.sort(Comparator.comparingInt(ImageEffect::getPriority));
    }

    /**
     * Execute all effects in the pipeline.
     * Effects are applied in priority order, with each effect including
     * the output of the previous one.
     *
     * @param initialContext The initial {@link EffectContext context} with the base image
     *
     * @return Final {@link EffectContext context} after all effects have been applied
     */
    public EffectContext execute(EffectContext initialContext) {
        EffectContext current = initialContext;

        for (ImageEffect effect : effects) {
            if (effect.canApply(current)) {
                log.debug("Applying effect: {} (priority: {})", effect.getName(), effect.getPriority());
                EffectResult result = effect.apply(current);
                current = result.toContext(current);
            } else {
                log.debug("Skipping effect {} (conditions not met)", effect.getName());
            }
        }

        return current;
    }

    public static class Builder {
        private final List<ImageEffect> effects = new ArrayList<>();

        /**
         * Add an {@link ImageEffect effect} to the pipeline.
         *
         * @param effect The {@link ImageEffect effect} to add
         *
         * @return This builder
         */
        public Builder addEffect(ImageEffect effect) {
            if (effect != null) {
                effects.add(effect);
            }

            return this;
        }

        /**
         * Build the effect pipeline.
         *
         * @return New {@link EffectPipeline} instance
         */
        public EffectPipeline build() {
            return new EffectPipeline(effects);
        }
    }
}