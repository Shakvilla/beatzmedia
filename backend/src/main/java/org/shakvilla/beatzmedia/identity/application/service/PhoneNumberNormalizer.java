package org.shakvilla.beatzmedia.identity.application.service;

import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/** Normalizes Ghana phone numbers to E.164 for account matching and verification. */
final class PhoneNumberNormalizer {
  private PhoneNumberNormalizer() {}

  static String normalizeGhana(String input) {
    if (input == null) throw new ValidationException("A valid phone number is required", "phone");
    String compact = input.trim();
    String digits = input.replaceAll("\\D", "");
    if (compact.startsWith("+") && digits.length() >= 7 && digits.length() <= 15) return "+" + digits;
    if (digits.startsWith("00")) digits = digits.substring(2);
    if (digits.startsWith("233") && digits.length() == 12) return "+" + digits;
    if (digits.startsWith("0") && digits.length() == 10) return "+233" + digits.substring(1);
    if (digits.length() == 9) return "+233" + digits;
    throw new ValidationException("Enter a phone number with a country code or a valid Ghana local number", "phone");
  }
}
