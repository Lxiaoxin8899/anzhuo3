package com.example.smartdosing.dosing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightWarningEvaluatorTest {

    private val config = WeightWarningConfig(tolerancePermille = 10)

    @Test
    fun targetNotValidReturnsIdle() {
        val result = WeightWarningEvaluator.evaluate(actualWeight = 1.0, targetWeight = 0.0, config = config)

        assertEquals(WeightWarningLevel.IDLE, result.level)
        assertFalse(result.allowManualConfirm)
        assertFalse(result.allowAutoConfirm)
    }

    @Test
    fun zeroWeightReturnsIdle() {
        val result = WeightWarningEvaluator.evaluate(actualWeight = 0.0, targetWeight = 100.0, config = config)

        assertEquals(WeightWarningLevel.IDLE, result.level)
        assertFalse(result.allowManualConfirm)
    }

    @Test
    fun halfTargetReturnsFilling() {
        val result = WeightWarningEvaluator.evaluate(actualWeight = 50.0, targetWeight = 100.0, config = config)

        assertEquals(WeightWarningLevel.FILLING, result.level)
    }

    @Test
    fun eightyFivePercentReturnsApproaching() {
        val result = WeightWarningEvaluator.evaluate(actualWeight = 85.0, targetWeight = 100.0, config = config)

        assertEquals(WeightWarningLevel.APPROACHING, result.level)
    }

    @Test
    fun ninetyTwoPercentReturnsSlowDown() {
        val result = WeightWarningEvaluator.evaluate(actualWeight = 92.0, targetWeight = 100.0, config = config)

        assertEquals(WeightWarningLevel.SLOW_DOWN, result.level)
    }

    @Test
    fun ninetySevenPercentBeforeToleranceReturnsFineDosing() {
        val result = WeightWarningEvaluator.evaluate(actualWeight = 97.0, targetWeight = 100.0, config = config)

        assertEquals(WeightWarningLevel.FINE_DOSING, result.level)
    }

    @Test
    fun inToleranceAllowsManualAndAutoConfirm() {
        val result = WeightWarningEvaluator.evaluate(actualWeight = 100.8, targetWeight = 100.0, config = config)

        assertEquals(WeightWarningLevel.IN_TOLERANCE, result.level)
        assertTrue(result.allowManualConfirm)
        assertTrue(result.allowAutoConfirm)
    }

    @Test
    fun overLimitBlocksAutoConfirm() {
        val result = WeightWarningEvaluator.evaluate(actualWeight = 101.5, targetWeight = 100.0, config = config)

        assertEquals(WeightWarningLevel.OVER_LIMIT, result.level)
        assertFalse(result.allowManualConfirm)
        assertFalse(result.allowAutoConfirm)
    }

    @Test
    fun hardOverLimitUsesToleranceMultiplier() {
        val result = WeightWarningEvaluator.evaluate(actualWeight = 103.0, targetWeight = 100.0, config = config)

        assertEquals(WeightWarningLevel.HARD_OVER_LIMIT, result.level)
    }

    @Test
    fun smallWeightHardOverLimitUsesMinimumAbsoluteGram() {
        val result = WeightWarningEvaluator.evaluate(actualWeight = 0.56, targetWeight = 0.5, config = config)

        assertEquals(WeightWarningLevel.HARD_OVER_LIMIT, result.level)
    }

    @Test
    fun hardOverLimitRequiresAdminWhenStrategyEnabled() {
        val strictConfig = config.copy(overLimitLockMode = OverLimitLockMode.HARD_ON_SEVERE)

        val result = WeightWarningEvaluator.evaluate(actualWeight = 103.0, targetWeight = 100.0, config = strictConfig)

        assertTrue(result.requireAdminUnlock)
    }
}
