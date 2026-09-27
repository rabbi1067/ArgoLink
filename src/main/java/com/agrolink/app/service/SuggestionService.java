package com.agrolink.app.service;

import com.agrolink.app.dto.SuggestionDTO;

import java.util.List;

/**
 * Personalised marketplace picks for a buyer, derived from their own orders
 * and offers plus platform-wide demand. Returns an empty list when the buyer
 * has no history at all - the frontend hides the whole section then.
 */
public interface SuggestionService {

    List<SuggestionDTO> suggestForBuyer(String buyerId);
}
