package com.focusforge.identity.profile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LevelingPolicyTest {

    @Test
    void curveGrowsAsNToTheOnePointFive() {
        assertThat(LevelingPolicy.xpToAdvance(1)).isEqualTo(100);
        assertThat(LevelingPolicy.xpToAdvance(2)).isEqualTo(283);
        assertThat(LevelingPolicy.xpToAdvance(3)).isEqualTo(520);
        assertThat(LevelingPolicy.xpToAdvance(4)).isEqualTo(800);
    }

    @Test
    void gainBelowThresholdStaysOnLevel() {
        var p = LevelingPolicy.gain(1, 40, 50);
        assertThat(p.level()).isEqualTo(1);
        assertThat(p.xpInLevel()).isEqualTo(90);
        assertThat(p.leveledUp()).isFalse();
    }

    @Test
    void exactThresholdLevelsUpWithZeroCarry() {
        var p = LevelingPolicy.gain(1, 50, 50);
        assertThat(p.level()).isEqualTo(2);
        assertThat(p.xpInLevel()).isZero();
        assertThat(p.levelsGained()).isEqualTo(1);
    }

    @Test
    void largeGainRollsOverMultipleLevels() {
        // 100 (L1->2) + 283 (L2->3) = 383; 400 leaves 17 into level 3
        var p = LevelingPolicy.gain(1, 0, 400);
        assertThat(p.level()).isEqualTo(3);
        assertThat(p.xpInLevel()).isEqualTo(17);
        assertThat(p.levelsGained()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> LevelingPolicy.gain(1, 0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LevelingPolicy.xpToAdvance(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
