package com.example.domain.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Exact BigDecimal recursive-descent arithmetic evaluator supporting +, -, ×, ÷, %, decimals, and parentheses.
 */
object ExpressionEvaluator {

    private val MC = MathContext(16, RoundingMode.HALF_UP)

    fun evaluate(rawExpression: String): Result<String> = runCatching {
        val normalized = rawExpression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("\\s+".toRegex(), "")
        require(normalized.isNotEmpty()) { "Empty expression" }

        val parser = Parser(normalized)
        val value = parser.parseExpression()
        require(parser.pos == normalized.length) { "Unexpected character at position ${parser.pos}" }

        val stripped = value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        if (stripped.compareTo(BigDecimal.ZERO) == 0) "0" else stripped.toPlainString()
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun parseExpression(): BigDecimal {
            var x = parseTerm()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; x = x.add(parseTerm(), MC) }
                    '-' -> { pos++; x = x.subtract(parseTerm(), MC) }
                    else -> return x
                }
            }
            return x
        }

        private fun parseTerm(): BigDecimal {
            var x = parseFactor()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; x = x.multiply(parseFactor(), MC) }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Division by zero" }
                        x = x.divide(divisor, MC)
                    }
                    else -> return x
                }
            }
            return x
        }

        private fun parseFactor(): BigDecimal {
            if (pos < s.length && s[pos] == '+') {
                pos++
                return parseFactor()
            }
            if (pos < s.length && s[pos] == '-') {
                pos++
                return parseFactor().negate()
            }

            var x: BigDecimal
            val startPos = pos
            if (pos < s.length && s[pos] == '(') {
                pos++
                x = parseExpression()
                require(pos < s.length && s[pos] == ')') { "Missing closing parenthesis" }
                pos++
            } else {
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) {
                    pos++
                }
                require(pos > startPos) { "Invalid token at $startPos" }
                x = BigDecimal(s.substring(startPos, pos))
            }

            while (pos < s.length && s[pos] == '%') {
                pos++
                x = x.divide(BigDecimal(100), MC)
            }
            return x
        }
    }
}
