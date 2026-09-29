package com.abhilekh.app.core.masking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Synthetic Aadhaar Benchmark and Verhoeff Algorithm Unit Test Suite.
 * Generates mathematically valid and corrupted synthetic Aadhaar numbers
 * to benchmark validation accuracy with zero privacy exfiltration.
 */
class AadhaarMaskingBenchmarkTest {

    private fun generateValidAadhaarNumber(): String {
        val firstDigit = Random.nextInt(2, 10).toString()
        val next10Digits = (1..10).map { Random.nextInt(0, 10) }.joinToString("")
        val partial11 = firstDigit + next10Digits
        val checksum = VerhoeffAlgorithm.generateVerhoeff(partial11)
        return partial11 + checksum
    }

    @Test
    fun benchmarkValidAadhaarGenerationAndValidation() {
        var validCount = 0
        val totalCards = 1000

        repeat(totalCards) {
            val uid = generateValidAadhaarNumber()
            if (VerhoeffAlgorithm.validateVerhoeff(uid)) {
                validCount++
            }
        }

        assertEquals("All synthetically generated valid Aadhaar numbers must pass Verhoeff validation", totalCards, validCount)
    }

    @Test
    fun benchmarkCorruptedAadhaarRejection() {
        var caughtSingleDigitErrors = 0
        var caughtTranspositionErrors = 0
        val iterations = 1000

        repeat(iterations) {
            val validUid = generateValidAadhaarNumber()

            // 1. Single digit alteration error
            val corruptedDigitIndex = Random.nextInt(0, 11)
            val currentDigit = validUid[corruptedDigitIndex].digitToInt()
            val newDigit = (currentDigit + Random.nextInt(1, 10)) % 10
            val corruptedSingle = validUid.substring(0, corruptedDigitIndex) + newDigit + validUid.substring(corruptedDigitIndex + 1)
            
            if (!VerhoeffAlgorithm.validateVerhoeff(corruptedSingle)) {
                caughtSingleDigitErrors++
            }

            // 2. Adjacent transposition error
            val swapIndex = Random.nextInt(0, 10)
            val charArray = validUid.toCharArray()
            if (charArray[swapIndex] != charArray[swapIndex + 1]) {
                val temp = charArray[swapIndex]
                charArray[swapIndex] = charArray[swapIndex + 1]
                charArray[swapIndex + 1] = temp
                val transposedUid = String(charArray)
                if (!VerhoeffAlgorithm.validateVerhoeff(transposedUid)) {
                    caughtTranspositionErrors++
                }
            } else {
                // If adjacent characters were identical, count as caught by default
                caughtTranspositionErrors++
            }
        }

        // Verhoeff algorithm is mathematically proven to catch 100% of single digit errors
        assertEquals("Must catch 100% of single digit substitution errors", iterations, caughtSingleDigitErrors)
        assertTrue("Must catch adjacent transposition errors", caughtTranspositionErrors >= (iterations * 0.98))
    }

    @Test
    fun testMalformedAadhaarEdgeCases() {
        assertFalse(VerhoeffAlgorithm.validateVerhoeff(""))
        assertFalse(VerhoeffAlgorithm.validateVerhoeff("123"))
        assertFalse(VerhoeffAlgorithm.validateVerhoeff("12345678901")) // 11 digits
        assertFalse(VerhoeffAlgorithm.validateVerhoeff("1234567890123")) // 13 digits
        assertFalse(VerhoeffAlgorithm.validateVerhoeff("ABCD1234EFGH"))
    }
}
