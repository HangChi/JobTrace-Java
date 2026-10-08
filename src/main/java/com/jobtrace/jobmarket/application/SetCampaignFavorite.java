package com.jobtrace.jobmarket.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SetCampaignFavorite {

    private final CampaignFavoriteCommand command;

    public SetCampaignFavorite(CampaignFavoriteCommand command) {
        this.command = command;
    }

    public Optional<FavoriteState> execute(
            String ownerId, UUID campaignId, boolean favorite) {
        if (!command.setFavorite(ownerId, campaignId, favorite)) {
            return Optional.empty();
        }
        return Optional.of(new FavoriteState(campaignId, favorite));
    }

    public record FavoriteState(UUID campaignId, boolean isFavorite) {
    }
}
