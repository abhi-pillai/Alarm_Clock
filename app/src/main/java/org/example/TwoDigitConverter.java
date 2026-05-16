package org.example;

import javafx.util.StringConverter;

public class TwoDigitConverter extends StringConverter<Integer> {

    private final int wrap; // 24 for hours, 60 for minutes

    public TwoDigitConverter(int wrap) {
        this.wrap = wrap;
    }

    @Override
    public String toString(Integer value) {
        // Always show two digits: 7 → "07", 23 → "23"
        return String.format("%02d", value);
    }

    @Override
    public Integer fromString(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            // Clamp to valid range if user types something out of bounds
            return Math.max(0, Math.min(value, wrap - 1));
        } catch (NumberFormatException e) {
            return 0; // Fall back to 0 on invalid input
        }
    }
}